package bin.cnpcplus.mixin.stats;

import net.minecraft.network.datasync.DataParameter;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Let arrows use their configured wall-stick flag instead of an unconditional arrow exception. */
@Mixin(value = EntityProjectile.class, remap = false)
public abstract class MixinProjectileWallStick {
    @Shadow @Final private static DataParameter<Boolean> Sticks;

    @Inject(method = "sticksToWalls", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$arrowsUseStickSetting(CallbackInfoReturnable<Boolean> cir) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        if (self.isArrow()) cir.setReturnValue(self.getDataManager().get(Sticks));
    }

    @Redirect(method = "func_70071_h_()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;isArrow()Z", ordinal = 0), require = 1)
    private boolean cnpcplus$groundUsesStickSetting(EntityProjectile self) {
        return false;
    }

    @Redirect(method = "func_70184_a(Lnet/minecraft/util/math/RayTraceResult;)V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;isArrow()Z", ordinal = 3), require = 1)
    private boolean cnpcplus$blockImpactUsesStickSetting(EntityProjectile self) {
        return false;
    }
}
