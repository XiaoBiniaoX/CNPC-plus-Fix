package top.cnpcplus.mixin;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PostPass.class, remap = false)
public class MixinShaderPostPass {
    @Inject(method = "process", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EffectInstance;apply()V"), require = 1)
    private void cnpcplus$uniforms(float partial, CallbackInfo ci) {
        top.cnpcplus.shader.client.ClientShaders.preparePost((PostPass)(Object)this);
    }

    @Inject(method = "process", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;draw(Lcom/mojang/blaze3d/vertex/MeshData;)V"), require = 1)
    private void cnpcplus$draw(float partial, CallbackInfo ci) {
        top.cnpcplus.shader.client.ClientShaders.preparePostDraw((PostPass)(Object)this);
    }
}
