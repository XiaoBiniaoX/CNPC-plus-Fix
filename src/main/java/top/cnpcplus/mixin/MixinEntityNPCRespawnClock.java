package top.cnpcplus.mixin;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.respawn.RespawnClock;
import top.cnpcplus.respawn.RespawnTimer;

/** Keep native death animation, spawn-cycle restrictions and reset; replace only the deadline comparison. */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCRespawnClock {
    @Unique private RespawnTimer cnpcplus$respawnTimer;
    @Unique private boolean cnpcplus$clockEnabled() { return CnpcPlusConfig.NPC_RESPAWN_GAME_CLOCK.get(); }
    @Unique private RespawnTimer cnpcplus$timer(EntityNPCInterface npc, RespawnClock clock, long wall) {
        boolean enabled = cnpcplus$clockEnabled();
        if (cnpcplus$respawnTimer == null) {
            long remaining = npc.killedtime > 0 ? RespawnTimer.remaining(npc.killedtime, wall) : Math.max(0L, npc.stats.respawnTime) * 1000L;
            cnpcplus$respawnTimer = RespawnTimer.start(npc.getUUID(), clock, remaining, enabled, wall);
        }
        cnpcplus$respawnTimer = cnpcplus$respawnTimer.rebind(npc.getUUID(), clock, enabled, wall);
        if (!enabled) npc.killedtime = cnpcplus$respawnTimer.wallDeadline();
        return cnpcplus$respawnTimer;
    }
    @Inject(method = "remove", at = @At(value = "INVOKE", target = "Lnoppes/npcs/roles/RoleInterface;killed()V"), require = 1)
    private void cnpcplus$recordDeath(net.minecraft.world.entity.Entity.RemovalReason reason, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.level() instanceof ServerLevel level) {
            RespawnClock clock = RespawnClock.get(level);
            long wall = System.currentTimeMillis();
            if (cnpcplus$respawnTimer == null)
                cnpcplus$respawnTimer = RespawnTimer.start(npc.getUUID(), clock, Math.max(0L, npc.stats.respawnTime) * 1000L, cnpcplus$clockEnabled(), wall);
            cnpcplus$timer(npc, clock, wall);
        }
    }
    @Inject(method = "tickDeath", at = @At("HEAD"), require = 1)
    private void cnpcplus$convertBeforeNativeRead(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.level() instanceof ServerLevel level && npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4)
            cnpcplus$timer(npc, RespawnClock.get(level), System.currentTimeMillis());
    }
    @Redirect(method = "tickDeath", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), require = 1)
    private long cnpcplus$compareGameDeadline() {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        long wall = System.currentTimeMillis();
        if (!(npc.level() instanceof ServerLevel level)) return wall;
        RespawnClock clock = RespawnClock.get(level);
        RespawnTimer timer = cnpcplus$timer(npc, clock, wall);
        if (!cnpcplus$clockEnabled()) return wall;
        return timer.ready(clock.now()) ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"), require = 1)
    private void cnpcplus$loadDeadline(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        cnpcplus$respawnTimer = null;
        if (npc.level() instanceof ServerLevel level && npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4
                && (npc.killedtime > 0 || npc.getHealth() <= 0)) {
            long wall = System.currentTimeMillis();
            RespawnClock clock = RespawnClock.get(level);
            cnpcplus$respawnTimer = RespawnTimer.read(tag, npc.getUUID(), clock, cnpcplus$clockEnabled(), wall);
            cnpcplus$timer(npc, clock, wall);
        }
        // No reset while NBT is being read: native tickDeath compares after safe loading.
    }
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveDeadline(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.level() instanceof ServerLevel level && npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4
                && (npc.killedtime > 0 || npc.getHealth() <= 0)) {
            RespawnClock clock = RespawnClock.get(level);
            cnpcplus$timer(npc, clock, System.currentTimeMillis()).write(tag, clock.now());
            tag.putLong("KilledTime", npc.killedtime);
        } else tag.remove(RespawnTimer.KEY);
    }
    @Inject(method = "reset", at = @At("HEAD"), require = 1)
    private void cnpcplus$clearDeadline(CallbackInfo ci) { cnpcplus$respawnTimer = null; }
}
