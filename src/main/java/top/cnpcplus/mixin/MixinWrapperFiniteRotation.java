package top.cnpcplus.mixin;

import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
import noppes.npcs.api.wrapper.EntityWrapper;
import noppes.npcs.api.wrapper.PlayerWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NPCWrapper calls EntityLivingBaseWrapper, which writes body yaw without vanilla validation. */
@Mixin(value = {EntityWrapper.class, EntityLivingBaseWrapper.class, PlayerWrapper.class}, remap = false)
public class MixinWrapperFiniteRotation {
    @Inject(method = "setRotation(F)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$finiteRotation(float rotation, CallbackInfo ci) {
        if (!Float.isFinite(rotation)) {
            throw new CustomNPCsException("setRotation requires finite numbers, got " + rotation);
        }
    }
}
