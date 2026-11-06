package top.cnpcplus.mixin;

import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import net.minecraft.client.renderer.EffectInstance;

@Mixin(value = PostPass.class, remap = false)
public class MixinShaderPostPass {
    @Redirect(method = "process", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EffectInstance;apply()V"), require = 1)
    private void cnpcplus$apply(EffectInstance effect) {
        top.cnpcplus.shader.client.ClientShaders.applyPost((PostPass)(Object)this);
    }
}
