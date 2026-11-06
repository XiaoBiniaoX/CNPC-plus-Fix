package top.cnpcplus.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ranged.RangedSpinStore;
import top.cnpcplus.ranged.ProjectileSpinAccess;

/** Projectile-side spin snapshot and per-axis rotation consumer. */
@Mixin(value = EntityProjectile.class, remap = false)
public class MixinEntityProjectileSpin implements ProjectileSpinAccess {

    // Mixin appends these initializers to the target <clinit>, after all native IDs.
    @Unique private static final EntityDataAccessor<Float> cnpcplus$spinX =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> cnpcplus$spinY =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> cnpcplus$spinZ =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);

    @Override public boolean cnpcplus$hasCustomSpin() {
        var data = ((EntityProjectile)(Object)this).getEntityData();
        return data.get(cnpcplus$spinX) != 0.0f || data.get(cnpcplus$spinY) != 0.0f || data.get(cnpcplus$spinZ) != 0.0f;
    }

    @Inject(method = "defineSynchedData", at = @At("RETURN"), require = 1, remap = false)
    private void cnpcplus$defineSpinFactors(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(cnpcplus$spinX, 0.0f);
        builder.define(cnpcplus$spinY, 0.0f);
        builder.define(cnpcplus$spinZ, 0.0f);
    }

    @Inject(method = "getStatProperties", at = @At("RETURN"), require = 1, remap = false)
    private void cnpcplus$snapshotSpinFactors(DataRanged stats, CallbackInfo ci) {
        if (stats == null) return;
        EntityProjectile projectile = (EntityProjectile) (Object) this;
        float x = RangedSpinStore.get(stats, RangedSpinStore.AXIS_X);
        float y = RangedSpinStore.get(stats, RangedSpinStore.AXIS_Y);
        float z = RangedSpinStore.get(stats, RangedSpinStore.AXIS_Z);
        SynchedEntityData data = projectile.getEntityData();
        data.set(cnpcplus$spinX, x);
        data.set(cnpcplus$spinY, y);
        data.set(cnpcplus$spinZ, z);

    }

    @Inject(method = "tick", at = @At("TAIL"), require = 1, remap = false)
    private void cnpcplus$advanceContinuousSpin(CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        SynchedEntityData data = self.getEntityData();
        float x = data.get(cnpcplus$spinX);
        float y = data.get(cnpcplus$spinY);
        float z = data.get(cnpcplus$spinZ);
        if (!self.level().isClientSide || self.isArrow() || !self.isRotating()) return;
        if (x == 0.0f && y == 0.0f && z == 0.0f) return;

        // The two preceding vanilla calls already align the projectile with its
        // motion vector.  Keep that orientation intact and accumulate the
        // configured XYZ spin separately for the renderer.  Mutating X/Y here
        // only changes the flight-facing angle and is overwritten by vanilla
        // on the next tick, which made the old implementation look like a
        // one-time launch rotation rather than continuous spin.
        float step = self.getSpeed();
        RangedSpinStore.advanceAngles(self, x * step, y * step, z * step);
    }
}
