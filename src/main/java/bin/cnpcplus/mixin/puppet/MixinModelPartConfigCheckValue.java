package bin.cnpcplus.mixin.puppet;

import noppes.npcs.ModelPartConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Official ModelPartConfig clamps scale to 0.5-1.5.
 * Extend max to 10 for equip editor.
 *
 * CRITICAL: NBT getFloat(missing) returns 0.0. Official checkValue(0, 0.5, 1.5)
 * clamped to 0.5 so limbs stayed visible. If we allow 0 through, new NPCs
 * render with scale 0 = missing legs/arms. Treat 0 as default 1.0.
 */
@Mixin(value = ModelPartConfig.class, remap = false)
public class MixinModelPartConfigCheckValue {

    @Inject(method = "checkValue", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$checkValue(float given, float min, float max, CallbackInfoReturnable<Float> cir) {
        if ((min == 0.5f && max == 1.5f) || (min == 0.0f && max == 2.0f)) {
            // absent NBT key => 0.0; restore default scale instead of invisible limb
            if (given == 0.0f) {
                cir.setReturnValue(Float.valueOf(1.0f));
                return;
            }
            if (given < 0.01f) {
                cir.setReturnValue(Float.valueOf(0.01f));
                return;
            }
            if (given > 10.0f) {
                cir.setReturnValue(Float.valueOf(10.0f));
                return;
            }
            cir.setReturnValue(Float.valueOf(given));
        }
    }

    // setScale(FFF): only raise max 1.5 -> 10; keep a tiny min so limbs never vanish
    @ModifyConstant(method = "setScale(FFF)V", constant = @Constant(floatValue = 0.5f), remap = false)
    private float cnpcplus$minScale(float original) {
        return 0.01f;
    }

    @ModifyConstant(method = "setScale(FFF)V", constant = @Constant(floatValue = 1.5f), remap = false)
    private float cnpcplus$maxScale(float original) {
        return 10.0f;
    }
}
