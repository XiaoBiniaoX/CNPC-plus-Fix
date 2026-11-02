package top.cnpcplus.mixin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketToolMobSpawner;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All living members use native lifecycle/targets; boats/minecarts remain owned by their summon group. */
@Mixin(value = JobSpawner.class, remap = false)
public abstract class MixinJobSpawnerMounts {
    @Shadow private String id;
    @Shadow private int number;
    @Shadow private void setTarget(LivingEntity entity, LivingEntity target) {}
    @Unique private final List<Entity> cnpcplus$mounts = new ArrayList<>();

    @Redirect(method = "spawnEntity(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/LivingEntity;",
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/packets/server/SPacketToolMobSpawner;spawnClone(Lnet/minecraft/nbt/CompoundTag;DDDLnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;"), require = 1)
    private Entity cnpcplus$trackWholeSummon(CompoundTag tag, double x, double y, double z, Level world) {
        Entity root = SPacketToolMobSpawner.spawnClone(tag, x, y, z, world);
        if (root == null) return null;
        List<Entity> members = root.getSelfAndPassengers().toList();
        LivingEntity primary = members.stream().filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).findFirst().orElse(null);
        if (primary == null) {
            members.forEach(Entity::discard); // Native spawner cannot track a summon with no living member.
            return null;
        }
        JobSpawner self = (JobSpawner)(Object)this;
        UUID group = UUID.randomUUID();
        for (Entity member : members) {
            CompoundTag data = member.getPersistentData();
            data.putString("NpcSpawnerId", id);
            data.putInt("NpcSpawnerNr", number);
            data.putUUID("CNPCPlusSpawnGroup", group);
            if (member instanceof LivingEntity living) {
                if (living != primary) {
                    self.spawned.add(living);
                    setTarget(living, self.npc.getTarget());
                }
                if (living instanceof EntityNPCInterface npc) {
                    npc.stats.spawnCycle = 4;
                    npc.stats.respawnTime = 0;
                    npc.ais.returnToStart = false;
                }
            } else cnpcplus$mounts.add(member);
        }
        return primary; // The native caller configures/adds this one exactly once and keeps its API return type.
    }

    @Inject(method = "checkSpawns", at = @At("RETURN"), require = 1)
    private void cnpcplus$removeOrphanMounts(CallbackInfo ci) {
        cnpcplus$pruneMounts(((JobSpawner)(Object)this).spawned);
    }

    @Unique
    private void cnpcplus$pruneMounts(List<LivingEntity> spawned) {
        var active = new HashSet<UUID>();
        for (LivingEntity living : spawned) {
            CompoundTag data = living.getPersistentData();
            if (data.hasUUID("CNPCPlusSpawnGroup")) active.add(data.getUUID("CNPCPlusSpawnGroup"));
        }
        cnpcplus$mounts.removeIf(mount -> {
            if (mount.isRemoved()) return true;
            if (active.contains(mount.getPersistentData().getUUID("CNPCPlusSpawnGroup"))) return false;
            mount.discard();
            return true;
        });
    }

    @Inject(method = "getNearbySpawned", at = @At("RETURN"), require = 1)
    private void cnpcplus$recoverMounts(CallbackInfoReturnable<List<LivingEntity>> cir) {
        JobSpawner self = (JobSpawner)(Object)this;
        cnpcplus$mounts.clear();
        for (Entity entity : self.npc.level().getEntitiesOfClass(Entity.class, self.npc.getBoundingBox().inflate(40))) {
            CompoundTag data = entity.getPersistentData();
            if (!(entity instanceof LivingEntity) && !entity.isRemoved() && id.equals(data.getString("NpcSpawnerId"))
                    && data.hasUUID("CNPCPlusSpawnGroup")) cnpcplus$mounts.add(entity);
        }
        cnpcplus$pruneMounts(cir.getReturnValue());
    }

    @Inject(method = "removeAllSpawned", at = @At("RETURN"), require = 1)
    private void cnpcplus$removeMounts(CallbackInfo ci) {
        cnpcplus$mounts.forEach(Entity::discard);
        cnpcplus$mounts.clear();
    }
}
