package bin.cnpcplus.mixin.penetration;

import bin.cnpcplus.common.IProjectilePenetration;
import bin.cnpcplus.common.IRangedPenetration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-authoritative projectile penetration.
 *
 * Naming rules for this project (see findings.md):
 *  - MC classes are MCP-named at compile time, reobf converts them to SRG.
 *  - noppes classes are already SRG-named, and @Shadow against them is
 *    unreliable, so public fields are read through a cast instead.
 */
@Mixin(value = EntityProjectile.class, remap = false)
public class MixinEntityProjectilePenetration implements IProjectilePenetration {
    private static final int MAX_PENETRATION = 16;

    @Unique
    private int cnpcplus$penetration;
    @Unique
    private int cnpcplus$remainingHits = 1;
    @Unique
    private final Set<UUID> cnpcplus$hitEntities = new HashSet<UUID>();
    @Unique
    private boolean cnpcplus$impactEntity;
    @Unique
    private boolean cnpcplus$callbackHandled;
    @Unique
    private boolean cnpcplus$damageApplied;

    @Inject(method = "getStatProperties", at = @At("TAIL"), remap = false)
    private void cnpcplus$copyFromStats(DataRanged stats, CallbackInfo ci) {
        cnpcplus$penetration = clamp(((IRangedPenetration) stats).cnpcplus$getPenetration());
        cnpcplus$remainingHits = cnpcplus$penetration + 1;
    }

    @Inject(method = "func_70014_b", at = @At("TAIL"), remap = false)
    private void cnpcplus$writePenetration(NBTTagCompound compound, CallbackInfo ci) {
        compound.setInteger("ProjectilePenetration", cnpcplus$penetration);
        compound.setInteger("ProjectilePenetrationRemaining", cnpcplus$remainingHits);
        NBTTagList hits = new NBTTagList();
        for (UUID id : cnpcplus$hitEntities) {
            hits.appendTag(new NBTTagString(id.toString()));
        }
        compound.setTag("ProjectilePenetrationHitEntities", hits);
    }

    @Inject(method = "func_70037_a", at = @At("TAIL"), remap = false)
    private void cnpcplus$readPenetration(NBTTagCompound compound, CallbackInfo ci) {
        cnpcplus$penetration = compound.hasKey("ProjectilePenetration")
                ? clamp(compound.getInteger("ProjectilePenetration")) : 0;
        cnpcplus$remainingHits = compound.hasKey("ProjectilePenetrationRemaining")
                ? Math.max(0, Math.min(cnpcplus$penetration + 1,
                compound.getInteger("ProjectilePenetrationRemaining"))) : cnpcplus$penetration + 1;
        cnpcplus$hitEntities.clear();
        if (compound.hasKey("ProjectilePenetrationHitEntities")) {
            NBTTagList hits = compound.getTagList("ProjectilePenetrationHitEntities", 8);
            for (int i = 0; i < hits.tagCount(); ++i) {
                try {
                    cnpcplus$hitEntities.add(UUID.fromString(hits.getStringTagAt(i)));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed UUIDs from old or externally edited entity NBT.
                }
            }
        }
    }

    @Inject(method = "func_70184_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$prepareImpact(RayTraceResult result, CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        cnpcplus$impactEntity = result.entityHit != null;
        cnpcplus$callbackHandled = false;
        cnpcplus$damageApplied = false;
        if (!cnpcplus$impactEntity || self.world.isRemote) {
            return;
        }
        UUID id = result.entityHit.getUniqueID();
        if (!cnpcplus$hitEntities.add(id)) {
            // Already pierced this target: skip it and keep flying.
            double length = Math.sqrt(self.motionX * self.motionX
                    + self.motionY * self.motionY + self.motionZ * self.motionZ);
            if (length > 0.0) {
                self.setPosition(self.posX + self.motionX / length * 0.1,
                        self.posY + self.motionY / length * 0.1,
                        self.posZ + self.motionZ / length * 0.1);
            }
            ci.cancel();
            return;
        }
        if (cnpcplus$remainingHits > 1) {
            self.destroyedOnEntityHit = false;
        }
    }

    @Inject(method = "func_70184_a", at = @At("RETURN"), remap = false)
    private void cnpcplus$finishImpact(RayTraceResult result, CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        if (!cnpcplus$impactEntity || cnpcplus$callbackHandled || !cnpcplus$damageApplied
                || self.world.isRemote) {
            if (cnpcplus$impactEntity && !cnpcplus$callbackHandled && !cnpcplus$damageApplied
                    && result.entityHit != null) {
                cnpcplus$hitEntities.remove(result.entityHit.getUniqueID());
            }
            return;
        }
        if (cnpcplus$remainingHits > 0) {
            --cnpcplus$remainingHits;
        }
        // The tail redirect already let the vanilla kill through when this was the last
        // charge, so no setDead is needed here; doing it again would be redundant.
    }

    /**
     * The real reason penetration looked like it did nothing.
     *
     * func_70184_a ends with an unconditional kill (decompile L520-523):
     *     if (world.isRemote) return;
     *     if (isArrow()) return;
     *     if (sticksToWalls()) return;
     *     func_70106_y();
     * Clearing destroyedOnEntityHit only skips the *arrow* death path at L433,
     * so any non-arrow projectile (the CNPC default) still died on its first
     * entity hit and never reached a second target.
     *
     * func_70106_y is owned by EntityProjectile (constant pool #478, inherited,
     * not declared) and appears 3 times in this method: ordinal 0 = the arrow
     * path at L433, ordinal 1 = the explosive path at L518, ordinal 2 = the
     * unconditional tail kill at L523. Only ordinal 2 is redirected; the
     * explosive kill is left alone so explosive rounds still detonate once.
     */
    @Redirect(method = "func_70184_a", at = @At(value = "INVOKE", ordinal = 2,
            target = "Lnoppes/npcs/entity/EntityProjectile;func_70106_y()V"), remap = false)
    private void cnpcplus$keepFlyingWhilePiercing(EntityProjectile self) {
        // This redirect sits at bytecode offset 1373; the damage redirect is at 173 and
        // the RETURN handler runs at 1376, so damageApplied is already set here while
        // remainingHits still holds its pre-decrement value.
        //
        // Keep flying only when a pierce charge is left *beyond* this hit. Every other
        // case (block hit, damage refused, last charge) falls through to the vanilla
        // kill, so nothing that used to die now flies on forever.
        if (cnpcplus$impactEntity && cnpcplus$damageApplied && cnpcplus$remainingHits > 1) {
            return;
        }
        self.setDead();
    }

    @Redirect(method = "func_70184_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/Entity;func_70097_a(Lnet/minecraft/util/DamageSource;F)Z"), remap = false)
    private boolean cnpcplus$recordDamage(Entity target, DamageSource source, float amount) {
        boolean applied = target.attackEntityFrom(source, amount);
        cnpcplus$damageApplied = applied;
        return applied;
    }

    @Redirect(method = "func_70184_a", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityProjectile$IProjectileCallback;onImpact(Lnoppes/npcs/entity/EntityProjectile;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/Entity;)Z"), remap = false)
    private boolean cnpcplus$trackCallback(EntityProjectile.IProjectileCallback callback,
                                            EntityProjectile projectile, BlockPos pos, Entity entity) {
        boolean handled = callback.onImpact(projectile, pos, entity);
        cnpcplus$callbackHandled = handled;
        if (handled && entity != null) {
            cnpcplus$hitEntities.remove(entity.getUniqueID());
        }
        return handled;
    }

    @Override
    public int cnpcplus$getPenetration() {
        return cnpcplus$penetration;
    }

    @Override
    public void cnpcplus$setPenetration(int value) {
        cnpcplus$penetration = clamp(value);
        cnpcplus$remainingHits = cnpcplus$penetration + 1;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_PENETRATION, value));
    }
}
