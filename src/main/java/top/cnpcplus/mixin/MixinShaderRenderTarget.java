package top.cnpcplus.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = RenderTarget.class, remap = false)
public class MixinShaderRenderTarget {
    // Keep vanilla clear/bind hooks; only remove the unused depth bit for script main.
    @ModifyArg(method = "clear", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clear(IZ)V"), index = 0, require = 1)
    private int cnpcplus$clearMask(int mask) {
        return top.cnpcplus.shader.client.ClientShaders.postClearMask((RenderTarget)(Object)this, mask);
    }
}
