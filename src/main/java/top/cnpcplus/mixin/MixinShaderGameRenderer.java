package top.cnpcplus.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.shader.client.ClientShaders;

@Mixin(value = GameRenderer.class, remap = false)
public class MixinShaderGameRenderer {
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void cnpcplus$beginFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ClientShaders.beginFrame();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", shift = At.Shift.AFTER), require = 1)
    private void cnpcplus$processPost(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        ClientShaders.renderPendingPost();
    }
}
