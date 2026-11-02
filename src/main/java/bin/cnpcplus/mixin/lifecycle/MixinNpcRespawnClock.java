package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.respawn.RespawnClock;
import bin.cnpcplus.respawn.RespawnTimer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcRespawnClock {
    @Unique private RespawnTimer cnpcplus$timer;
    @Unique private RespawnTimer cnpcplus$timer(EntityNPCInterface npc, long wall) {
        RespawnClock clock = RespawnClock.get((WorldServer) npc.world);
        boolean enabled = CnpcPlusConfig.independentRespawnClock();
        if (cnpcplus$timer == null) cnpcplus$timer = RespawnTimer.start(npc.getUniqueID(), clock,
                npc.killedtime > 0 ? RespawnTimer.remaining(npc.killedtime, wall) : Math.max(0L, npc.stats.respawnTime) * 1000L, enabled, wall);
        cnpcplus$timer = cnpcplus$timer.rebind(npc.getUniqueID(), clock, enabled, wall);
        if (!enabled) npc.killedtime = cnpcplus$timer.wallDeadline();
        return cnpcplus$timer;
    }
    @Inject(method = "func_70106_y()V", at = @At("RETURN"), require = 1)
    private void cnpcplus$death(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.world instanceof WorldServer && npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4)
            cnpcplus$timer(npc, System.currentTimeMillis());
    }
    @Inject(method = "func_70609_aI()V", at = @At("HEAD"), require = 1)
    private void cnpcplus$beforeCompare(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.world instanceof WorldServer && npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4)
            cnpcplus$timer(npc, System.currentTimeMillis());
    }
    @Redirect(method = "func_70609_aI()V", at = @At(value = "INVOKE", target = "Ljava/lang/System;currentTimeMillis()J"), require = 1)
    private long cnpcplus$compare() {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this; long wall = System.currentTimeMillis();
        if (!(npc.world instanceof WorldServer)) return wall;
        RespawnTimer timer = cnpcplus$timer(npc, wall);
        return CnpcPlusConfig.independentRespawnClock() ? timer.ready() ? Long.MAX_VALUE : Long.MIN_VALUE : wall;
    }
    @Inject(method = "func_70037_a", at = @At("RETURN"), require = 1)
    private void cnpcplus$read(NBTTagCompound tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this; cnpcplus$timer = null;
        if (npc.world instanceof WorldServer && npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4 && (npc.killedtime > 0 || npc.getHealth() <= 0)) {
            long wall = System.currentTimeMillis();
            cnpcplus$timer = RespawnTimer.read(tag, npc.getUniqueID(), RespawnClock.get((WorldServer) npc.world), CnpcPlusConfig.independentRespawnClock(), wall);
            cnpcplus$timer(npc, wall);
        }
    }
    @Inject(method = "func_70014_b", at = @At("RETURN"), require = 1)
    private void cnpcplus$write(NBTTagCompound tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.world instanceof WorldServer && npc.stats.spawnCycle != 3 && npc.stats.spawnCycle != 4 && (npc.killedtime > 0 || npc.getHealth() <= 0)) {
            cnpcplus$timer(npc, System.currentTimeMillis()).write(tag); tag.setLong("KilledTime", npc.killedtime);
        } else tag.removeTag(RespawnTimer.KEY);
    }
    @Inject(method = "reset", at = @At("HEAD"), require = 1)
    private void cnpcplus$reset(CallbackInfo ci) { cnpcplus$timer = null; }
}
