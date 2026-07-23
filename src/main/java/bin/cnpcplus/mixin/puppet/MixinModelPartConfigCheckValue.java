package bin.cnpcplus.mixin.puppet;

import noppes.npcs.ModelPartConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelPartConfig.class, remap = false)
public class MixinModelPartConfigCheckValue {

    @Inject(method = "checkValue", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$extendScaleRange(float given, float min, float max, CallbackInfoReturnable<Float> cir) {
        if (min == 0.0f && max == 2.0f) {
            if (given < 0.0f) { cir.setReturnValue(0.0f); return; }
            if (given > 10.0f) { cir.setReturnValue(10.0f); return; }
            cir.setReturnValue(given);
        }
    }
}
