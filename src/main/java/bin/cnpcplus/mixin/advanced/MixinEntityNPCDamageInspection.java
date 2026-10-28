package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.ai.DamageInspectionController;
import net.minecraft.util.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 238-13：受伤记录弹道方向；任务表重建时补装视察 AI。 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCDamageInspection {

    @Inject(method = "updateTasks", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$installInspectionGoal(CallbackInfo ci) {
        DamageInspectionController.install((EntityNPCInterface) (Object) this);
    }

    @Inject(method = "func_70097_a(Lnet/minecraft/util/DamageSource;F)Z", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$inspectUnseenDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            DamageInspectionController.onHurt((EntityNPCInterface) (Object) this, source, amount);
        }
    }

    @Inject(method = "reset", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$clearInspectionOnReset(CallbackInfo ci) {
        DamageInspectionController.reset((EntityNPCInterface) (Object) this);
    }
}
