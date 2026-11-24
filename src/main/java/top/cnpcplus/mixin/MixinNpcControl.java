package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.NpcControlAccess;
import top.cnpcplus.ai.PursuitLimit;
import top.cnpcplus.ai.ReturnHomeState;
import top.cnpcplus.wand.WandFreezeState;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcControl implements NpcControlAccess {
    @Shadow @Final private static EntityDataAccessor<Boolean> Walking;
    @Unique private long cnpcplus$freezeEpoch = -1;
    @Unique private boolean cnpcplus$forcedReturning;
    @Unique private long cnpcplus$disengageUntil;

    @Override public boolean cnpcplus$isFrozen() {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        return cnpcplus$freezeEpoch >= 0 && npc.level() instanceof ServerLevel level
                && cnpcplus$freezeEpoch == WandFreezeState.get(level.getServer()).epoch();
    }
    @Override public void cnpcplus$setFrozen(boolean frozen) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        cnpcplus$freezeEpoch = frozen ? WandFreezeState.get(npc.getServer()).epoch() : -1;
        if (frozen) {
            npc.getNavigation().stop();
            npc.setDeltaMovement(Vec3.ZERO);
            npc.getEntityData().set(Walking, false);
        }
    }
    @Override public boolean cnpcplus$isDisengaging() {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        return ((PursuitLimit)npc.advanced).cnpcplus$isPursuitLimited() && npc.level().getGameTime() < cnpcplus$disengageUntil;
    }
    @Override public boolean cnpcplus$isForcedReturning() { return cnpcplus$forcedReturning; }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$controlTick(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        if (npc.level().isClientSide) return;
        if (cnpcplus$isFrozen()) { ci.cancel(); return; }
        if (CustomNpcs.FreezeNPCs) return;
        if (!((PursuitLimit)npc.advanced).cnpcplus$isPursuitLimited()) {
            cnpcplus$forcedReturning = false;
            cnpcplus$disengageUntil = 0;
            return;
        }
        var target = npc.getTarget();
        if (target != null && PursuitLimit.outside(npc, target)) {
            cnpcplus$disengageUntil = npc.level().getGameTime() + 60;
            cnpcplus$forcedReturning = true;
            npc.combatHandler.reset();
            // TargetLost scripts may veto normal loss; the configured limit must still apply.
            if (npc.getTarget() != null) ((MobPursuitTargetAccess)npc).cnpcplus$setRawTarget(null);
            npc.getNavigation().stop();
        } else if (cnpcplus$forcedReturning && (target != null || ReturnHomeState.arrived(npc, PursuitLimit.home(npc)))) {
            cnpcplus$forcedReturning = false;
        }
    }
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$freezeDamage(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (cnpcplus$isFrozen()) cir.setReturnValue(false);
    }
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveFrozen(CompoundTag tag, CallbackInfo ci) {
        if (cnpcplus$isFrozen()) {
            tag.putBoolean("CNPCPlusFrozen", true);
            tag.putLong("CNPCPlusFreezeEpoch", cnpcplus$freezeEpoch);
        }
    }
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"), require = 1)
    private void cnpcplus$loadFrozen(CompoundTag tag, CallbackInfo ci) {
        cnpcplus$freezeEpoch = tag.getBoolean("CNPCPlusFrozen") ? tag.getLong("CNPCPlusFreezeEpoch") : -1;
        if (cnpcplus$isFrozen()) cnpcplus$setFrozen(true);
        else cnpcplus$freezeEpoch = -1;
    }
}
