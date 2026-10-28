package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IRangedSpinAccess;
import bin.cnpcplus.common.ProjectileSpinKeys;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 发射时快照三轴系数，DataManager 同步，渲染端独立累积连续旋转。 */
@Mixin(value = EntityProjectile.class, remap = false)
public abstract class MixinEntityProjectileSpin {
    @Inject(method = "func_70088_a()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$registerSpinSpeed(CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        for (int axis = 0; axis < 3; axis++) self.getDataManager().register(ProjectileSpinKeys.key(axis), 0.0F);
    }

    @Inject(method = "getStatProperties", at = @At("TAIL"), require = 1)
    private void cnpcplus$snapshotSpinSpeed(DataRanged stats, CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        for (int axis = 0; axis < 3; axis++) {
            self.getDataManager().set(ProjectileSpinKeys.key(axis), ((IRangedSpinAccess) stats).cnpcplus$getSpinSpeed(axis));
        }
    }

    @Inject(method = "func_70014_b", at = @At("TAIL"), require = 1)
    private void cnpcplus$writeSpinSpeed(NBTTagCompound tag, CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        for (int axis = 0; axis < 3; axis++) tag.setFloat(ProjectileSpinKeys.NBT_KEYS[axis], self.getDataManager().get(ProjectileSpinKeys.key(axis)));
    }

    @Inject(method = "func_70037_a", at = @At("TAIL"), require = 1)
    private void cnpcplus$readSpinSpeed(NBTTagCompound tag, CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        for (int axis = 0; axis < 3; axis++) self.getDataManager().set(ProjectileSpinKeys.key(axis), ProjectileSpinKeys.clamp(tag.getFloat(ProjectileSpinKeys.NBT_KEYS[axis])));
    }

    @Redirect(method = "func_70071_h_()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;getSpeed()F"), require = 1)
    private float cnpcplus$suppressConfiguredVanillaSpin(EntityProjectile self) {
        return ProjectileSpinKeys.configured(self) ? 0.0F : self.getSpeed();
    }

    @Inject(method = "func_70071_h_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$advanceSpin(CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        if (self.world.isRemote && !self.isArrow() && self.isRotating() && ProjectileSpinKeys.configured(self)) {
            ProjectileSpinKeys.advance(self);
        }
    }
}
