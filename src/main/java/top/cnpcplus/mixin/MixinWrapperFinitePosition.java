package top.cnpcplus.mixin;

import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.wrapper.EntityWrapper;
import noppes.npcs.api.wrapper.PlayerWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PlayerWrapper teleports directly instead of calling EntityWrapper.setPosition. */
@Mixin(value = {EntityWrapper.class, PlayerWrapper.class}, remap = false)
public class MixinWrapperFinitePosition {
    @Inject(method = "setPosition(DDD)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$finitePosition(double x, double y, double z, CallbackInfo ci) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new CustomNPCsException("setPosition requires finite numbers, got " + x + ", " + y + ", " + z);
        }
    }
}
