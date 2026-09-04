package bin.cnpcplus.mixin.font;

import noppes.npcs.shared.client.util.TrueTypeFont;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * CNPC TrueTypeFont 在 getOrCreateGlyph 内调用 GL11.glTexParameteri 设置 GL_TEXTURE_MIN_FILTER，
 * 但未设置 GL_TEXTURE_WRAP_S/T，导致中文等字符贴图边缘重复，产生上下错位。
 * 此 mixin 在设置完 MIN_FILTER 后补充设置 WRAP_S/T 为 GL_CLAMP_TO_EDGE。
 */
@Mixin(value = TrueTypeFont.class, remap = false)
public class MixinTrueTypeFontAtlas {

    /**
     * 重定向 getOrCreateGlyph 内 glTexParameteri 调用，原调用后补充 wrap 设置。
     */
    @Redirect(
            method = "getOrCreateGlyph",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL11;glTexParameteri(III)V"
            ),
            require = 1
    )
    private void cnpcplus$fixGlyphTextureWrap(int target, int pname, int param) {
        // 调用原函数设置 MIN_FILTER
        GL11.glTexParameteri(target, pname, param);

        // 当设置的是 TEXTURE_2D 的 MIN_FILTER 时，补充设置 WRAP_S/T 为 CLAMP_TO_EDGE
        // GL_TEXTURE_2D=3553, GL_TEXTURE_MIN_FILTER=10240
        if (target == 3553 && pname == 10240) {
            // GL_TEXTURE_WRAP_S=10242, GL_TEXTURE_WRAP_T=10243, GL_CLAMP_TO_EDGE=33071
            GL11.glTexParameteri(3553, 10242, 33071);
            GL11.glTexParameteri(3553, 10243, 33071);
        }
    }
}
