package top.cnpcplus.mixin;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PostPass.class)
public class MixinShaderPostPass {
    @Inject(method = "process", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EffectInstance;apply()V"), require = 1)
    private void cnpcplus$uniforms(float partial, CallbackInfo ci) {
        top.cnpcplus.shader.client.ClientShaders.bindPost(((PostPass)(Object)this).getEffect());
    }
}
