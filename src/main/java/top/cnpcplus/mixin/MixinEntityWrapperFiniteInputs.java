package top.cnpcplus.mixin;

import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fail in the script console before invalid numbers corrupt an entity or its render state. */
@Mixin(value = EntityWrapper.class, remap = false)
public class MixinEntityWrapperFiniteInputs {
    @Inject(method = {"setX(D)V", "setY(D)V", "setZ(D)V", "setMotionX(D)V", "setMotionY(D)V", "setMotionZ(D)V"},
            at = @At("HEAD"), require = 6)
    private void cnpcplus$finiteScalar(double value, CallbackInfo ci) {
        cnpcplus$checkFinite(value, ci);
    }

    @Inject(method = "setPitch(F)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$finitePitch(float value, CallbackInfo ci) {
        cnpcplus$checkFinite(value, ci);
    }

    @Inject(method = "knockback(IF)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$finiteKnockback(int power, float direction, CallbackInfo ci) {
        cnpcplus$checkFinite(direction, ci);
    }

    @Unique
    private static void cnpcplus$checkFinite(double value, CallbackInfo ci) {
        if (!Double.isFinite(value)) {
            throw new CustomNPCsException(ci.getId() + " requires finite numbers, got " + value);
        }
    }
}
