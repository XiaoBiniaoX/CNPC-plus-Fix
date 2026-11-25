package top.cnpcplus.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityProjectile;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep native impact callbacks, effects and lifetime; fix only the wall decision and landing. */
@Mixin(value = EntityProjectile.class, remap = false)
public abstract class MixinProjectileWallStick {
    @Shadow @Final private static EntityDataAccessor<Boolean> Sticks;

    @Inject(method = "sticksToWalls", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$arrowWallSetting(CallbackInfoReturnable<Boolean> cir) {
        EntityProjectile self = (EntityProjectile)(Object)this;
        // Arrows already have a 3D renderer, even when Render3D is off.
        if (self.isArrow()) cir.setReturnValue(self.getEntityData().get(Sticks));
    }

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;isArrow()Z", ordinal = 0), require = 1)
    private boolean cnpcplus$groundUsesSetting(EntityProjectile self) {
        return false; // The following sticksToWalls() call now reads the arrow's setting.
    }

    @Redirect(method = "m_6532_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;isArrow()Z", ordinal = 3), require = 1)
    private boolean cnpcplus$blockImpactUsesSetting(EntityProjectile self, HitResult hit) {
        return hit.getType() == HitResult.Type.BLOCK ? self.sticksToWalls() : self.isArrow() || self.sticksToWalls();
    }

    @Redirect(method = "m_6532_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;isArrow()Z", ordinal = 4), require = 1)
    private boolean cnpcplus$discardUnstuckArrow(EntityProjectile self, HitResult hit) {
        return hit.getType() == HitResult.Type.BLOCK ? self.sticksToWalls() : self.isArrow() || self.sticksToWalls();
    }

    @Inject(method = "m_6532_", at = @At(value = "FIELD",
            target = "Lnoppes/npcs/entity/EntityProjectile;inGround:Z", opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER), require = 1)
    private void cnpcplus$stopAtSurface(HitResult hit, CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile)(Object)this;
        // This point is reached only after the native callback accepts a sticking block hit.
        if (self.isArrow()) {
            Vec3 target = hit.getLocation();
            self.setPos(target.subtract(target.subtract(self.position()).normalize().scale(0.05)));
            self.setDeltaMovement(Vec3.ZERO); // Native tick adds velocity after onHit returns.
        }
    }
}
