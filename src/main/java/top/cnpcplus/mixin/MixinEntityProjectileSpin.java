package top.cnpcplus.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ranged.RangedSpinStore;

/** Projectile-side spin snapshot and per-axis rotation consumer. */
@Mixin(value = EntityProjectile.class, remap = false)
public class MixinEntityProjectileSpin {

    // Mixin appends these initializers to EntityProjectile's own <clinit>, after
    // its native data IDs. Loading DataRanged/GUI storage cannot reserve IDs.
    @Unique private static final EntityDataAccessor<Float> cnpcplus$spinX =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> cnpcplus$spinY =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);
    @Unique private static final EntityDataAccessor<Float> cnpcplus$spinZ =
            SynchedEntityData.defineId(EntityProjectile.class, EntityDataSerializers.FLOAT);

    @Inject(method = "m_8097_", at = @At("RETURN"), remap = false)
    private void cnpcplus$defineSpinFactors(CallbackInfo ci) {
        SynchedEntityData data = ((EntityProjectile) (Object) this).getEntityData();
        data.define(cnpcplus$spinX, 0.0f);
        data.define(cnpcplus$spinY, 0.0f);
        data.define(cnpcplus$spinZ, 0.0f);
    }

    @Inject(method = "getStatProperties", at = @At("RETURN"), remap = false)
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

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile;m_146926_(F)V",
            ordinal = 2), require = 1, remap = false)
    private void cnpcplus$replaceVanillaSpin(EntityProjectile self, float vanillaPitch) {
        SynchedEntityData data = self.getEntityData();
        float x = data.get(cnpcplus$spinX);
        float y = data.get(cnpcplus$spinY);
        float z = data.get(cnpcplus$spinZ);
        // Zero remains byte-for-byte vanilla behaviour. With any configured
        // axis, preserve the motion-facing pitch and let the renderer own all
        // configured continuous spin instead of mixing in vanilla's hardcoded
        // 10/20-degree X increment.
        self.setXRot(vanillaPitch);
    }

    @Inject(method = "m_8119_", at = @At("TAIL"), remap = false)
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
