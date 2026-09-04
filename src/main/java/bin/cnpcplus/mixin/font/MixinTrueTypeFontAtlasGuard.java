package bin.cnpcplus.mixin.font;

import net.minecraft.client.renderer.GlStateManager;
import noppes.npcs.config.TrueTypeFont;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * 修复 CNPC TrueTypeFont 图集越界导致的编辑器间断性乱码。
 *
 * 根因（已逐行验证）：TrueTypeFont.getOrCreateGlyph 只在换行后检查
 * {@code cache.y >= 512}，却从不检查 {@code cache.y + glyph.height <= 512}。
 * 例如 y=500、高=23 时，字形被 Graphics2D 裁掉，UV 下边界变为 1.021；纹理
 * 默认 GL_REPEAT，于是 v 回绕到图集顶部，把其他字形画到当前位置。坏 Glyph
 * 又会被 glyphcache 永久缓存，所以表现为「部分字偶发乱码，且同一批字持续乱」。
 *
 * 这里不覆写 CNPC 的私有 getOrCreateGlyph（那会复制整段 AWT / GL 分配逻辑，
 * 风险和维护成本都高），而做两层最小防护：
 *
 * 1. 分配前预留 64px 底部安全带。TrueTypeFont 的字形高度由 Java AWT
 *    FontMetrics 给出；在本测试包 FontSize=18 的情况下远小于 64。当前页
 *    x 已接近行尾且 y 进入安全带时，原版下一步必然换行；提前把当前页标 full，
 *    让原版 getCurrentTexture 直接创建新图集，从而避免 y+height 跨 512。
 *    这是针对主因的根治，不改任何字符、折行、NBT 或网络数据。
 *
 * 2. draw() 绑定每张字形纹理后强制 GL_CLAMP_TO_EDGE。即使未来某个字体的
 *    高度超过安全带，最坏也只是边缘透明 / 拉伸，绝不会回绕采样成另一个字。
 *
 * 为什么只影响编辑界面：GuiTextArea 使用的是独立静态 TrueTypeFont；标题、
 * 普通 FontRenderer 与网络文本都不走它，所以本修复不会改变玩家游玩时的文本。
 *
 * 客户端安全：目标是 CNPC 客户端字体类，混入仅注册在 client 数组；没有任何
 * 服务端类、网络包或存档格式改动。
 */
@Mixin(value = TrueTypeFont.class, remap = false)
public class MixinTrueTypeFontAtlasGuard {

    /** 512x512 图集底部预留高度，覆盖 18px 字体及所有已知 fallback 字形。 */
    @Unique
    private static final int CNPCPLUS_ATLAS_BOTTOM_GUARD = 64;

    @Unique
    private static Field cnpcplus$texturesField;

    @Unique
    private static Field cnpcplus$glyphsField;

    @Unique
    private static Field cnpcplus$cacheXField;

    @Unique
    private static Field cnpcplus$cacheYField;

    @Unique
    private static Field cnpcplus$cacheFullField;

    @Unique
    private static boolean cnpcplus$reflectionReady;

    /**
     * 原版开始分配新字形前，若即将发生换行且下一行已进入底部安全带，
     * 直接封存当前图集。原版随后调用 getCurrentTexture() 时会自然创建新页。
     */
    /**
     * 注意：getOrCreateGlyph 是有返回值的方法。即使这个 HEAD 注入只做副作用、
     * 完全不读取返回值，Mixin handler 仍必须用 CallbackInfoReturnable；
     * 使用 CallbackInfo 会在 APPLY 阶段报 "CallbackInfoReturnable is required"
     * 并让 TrueTypeFont 整类加载失败（3.4.0 首次实机启动崩溃）。
     */
    @Inject(method = "getOrCreateGlyph", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$avoidBottomOverflow(char character, CallbackInfoReturnable<?> cir) {
        try {
            cnpcplus$initReflection();
            if (!cnpcplus$reflectionReady) {
                return;
            }
            Map<?, ?> glyphs = (Map<?, ?>) cnpcplus$glyphsField.get(this);
            if (glyphs != null && glyphs.containsKey(Character.valueOf(character))) {
                return;
            }
            List<?> textures = (List<?>) cnpcplus$texturesField.get(this);
            if (textures == null) {
                return;
            }
            Object current = null;
            for (Object cache : textures) {
                if (!cnpcplus$cacheFullField.getBoolean(cache)) {
                    current = cache;
                    break;
                }
            }
            if (current == null) {
                return;
            }
            int x = cnpcplus$cacheXField.getInt(current);
            int y = cnpcplus$cacheYField.getInt(current);
            // 原版 glyph 宽度最少 1、常见中文宽约 18。用 64 做保守上界：
            // 只在「本次很可能换行」且下一行已无安全高度时换新页。
            if (x + CNPCPLUS_ATLAS_BOTTOM_GUARD >= 512
                    && y + CNPCPLUS_ATLAS_BOTTOM_GUARD >= 512) {
                cnpcplus$cacheFullField.setBoolean(current, true);
            }
        } catch (Exception ignored) {
            // 反射失败则保持 CNPC 原行为；后面的 GL_CLAMP 仍是独立兜底。
        }
    }

    /**
     * 原版绑定 glyph.texture 后马上把 wrap 改成 clamp。
     * 这条是 UV 越界的最后保险，防止 GL_REPEAT 回绕画出别的字形。
     */
    @Inject(
            method = "draw",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;func_179144_i(I)V", shift = At.Shift.AFTER),
            remap = false,
            require = 1)
    private void cnpcplus$clampGlyphTexture(String text, float x, float y, int color, CallbackInfo ci) {
        // GL_CLAMP_TO_EDGE 定义在 GL12，不在 GL11。
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    /**
     * 原版 dispose 只删 GL 纹理和 textcache，却保留 glyphcache / textures。
     * FontContainer.copy 又会共享同一个 TrueTypeFont，配置字体重载后可能继续
     * 用已释放的 texture id 画字，被其他 mod 复用时就是另一条乱码通路。
     */
    @Inject(method = "dispose", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$clearReleasedGlyphs(CallbackInfo ci) {
        try {
            cnpcplus$initReflection();
            if (!cnpcplus$reflectionReady) {
                return;
            }
            Map<?, ?> glyphs = (Map<?, ?>) cnpcplus$glyphsField.get(this);
            if (glyphs != null) {
                glyphs.clear();
            }
            List<?> textures = (List<?>) cnpcplus$texturesField.get(this);
            if (textures != null) {
                textures.clear();
            }
        } catch (Exception ignored) {
            // dispose 不能因清理失败而阻断原版字体切换。
        }
    }

    @Unique
    private static void cnpcplus$initReflection() throws Exception {
        if (cnpcplus$reflectionReady) {
            return;
        }
        synchronized (MixinTrueTypeFontAtlasGuard.class) {
            if (cnpcplus$reflectionReady) {
                return;
            }
            cnpcplus$texturesField = TrueTypeFont.class.getDeclaredField("textures");
            cnpcplus$glyphsField = TrueTypeFont.class.getDeclaredField("glyphcache");
            cnpcplus$texturesField.setAccessible(true);
            cnpcplus$glyphsField.setAccessible(true);
            Class<?> cacheClass = Class.forName("noppes.npcs.config.TrueTypeFont$TextureCache");
            cnpcplus$cacheXField = cacheClass.getDeclaredField("x");
            cnpcplus$cacheYField = cacheClass.getDeclaredField("y");
            cnpcplus$cacheFullField = cacheClass.getDeclaredField("full");
            cnpcplus$cacheXField.setAccessible(true);
            cnpcplus$cacheYField.setAccessible(true);
            cnpcplus$cacheFullField.setAccessible(true);
            cnpcplus$reflectionReady = true;
        }
    }
}
