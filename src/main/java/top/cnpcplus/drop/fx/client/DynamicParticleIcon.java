package top.cnpcplus.drop.fx.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * 粒子图标动态解析层（233d，纯客户端）——<b>mod 粒子零配置自动有图标</b>。
 *
 * <h3>原理（字节码实证）</h3>
 * {@code ParticleEngine.spriteSets}（SRG {@code f_107295_}，本 mod AT 开放）
 * 的 key={@code BuiltInRegistries.PARTICLE_TYPE.getKey(type)}——
 * 即<b>粒子注册 id 的 ResourceLocation</b>（vanilla {@code minecraft:flame}
 * 与任意 mod {@code mymod:myparticle} 同一命名空间），
 * value=MutableSpriteSet（实现 {@link SpriteSet}）。
 * {@code register()} 三条重载全部汇入
 * {@code register(ParticleType, SpriteParticleRegistration)}，
 * 该重载开头就 {@code spriteSets.put(getKey(type), new MutableSpriteSet())}
 * ——<b>凡注册过的粒子（含 mod）都有条目</b>；sprite 内容在
 * reload 时从粒子 JSON 填充。
 *
 * <h3>blit</h3>
 * {@link GuiGraphics#blit(int, int, int, int, int, TextureAtlasSprite)}
 * 直接吃 Sprite——内部用 sprite 自带的 atlasLocation/atlas 尺寸/帧位置
 * 采样，<b>无需</b>拿 atlas 纹理尺寸、无需算归一化 uv。
 * {@code SpriteSet} 只有 {@code get(age, lifetime)}，取 {@code get(0, 1)}
 * =首帧。空 SpriteSet（纯代码注册无粒子 JSON 的 dummy 条目）会抛
 * 越界——try-catch 兜住返回 false，调用方画 '?'。
 *
 * <h3>失败路径</h3>
 * 返回 false 的情形：粒子引擎未初始化、spriteSets 无该 id、
 * SpriteSet 为空（无 sprite 的代码粒子）、任何异常——全部安全降级 '?'。
 */
final class DynamicParticleIcon {

    private DynamicParticleIcon() {}

    /**
     * 尝试把粒子首帧图标画到 (x,y)（8×8）。
     *
     * @return true=已画；false=拿不到（调用方画 '?'）
     */
    static boolean tryBlit(GuiGraphics g, int x, int y, String particleId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ParticleEngine engine = mc.particleEngine;
            if (engine == null) return false;
            Object setObj = engine.spriteSets.get(new ResourceLocation(particleId));
            if (!(setObj instanceof SpriteSet set)) return false;
            TextureAtlasSprite sprite = set.get(0, 1);
            if (sprite == null) return false;
            g.blit(x, y, ParticleIconLookup.Icon.DRAW, ParticleIconLookup.Icon.DRAW, 0, sprite);
            return true;
        } catch (Throwable t) {
            // 空 SpriteSet / 引擎状态异常——降级 '?'
            return false;
        }
    }
}
