package bin.cnpcplus.mixin.spawner;

import bin.cnpcplus.common.RespawnCycleStore;
import bin.cnpcplus.spawner.SpawnerData;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NavigableMap;

@Mixin(value = JobSpawner.class, remap = false)
public abstract class MixinJobSpawnerLists {
    @Shadow private int number;
    @Shadow private EntityLivingBase spawnEntity(NBTTagCompound compound) { throw new AssertionError(); }
    @Shadow private List<EntityLivingBase> getNearbySpawned() { throw new AssertionError(); }
    @Unique private int cnpcplus$progress;
    @Unique private boolean cnpcplus$batchSpawned;
    @Unique private Deque<Integer> cnpcplus$batch;
    @Unique private long cnpcplus$recoveryUntil;

    @Inject(method = "readFromNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$readEntries(NBTTagCompound tag, CallbackInfo ci) {
        SpawnerData.read((JobSpawner) (Object) this, tag, SpawnerData.KEY);
        if (tag.hasKey("CNPCPlusSpawnerProgress", 99)) this.cnpcplus$progress = Math.max(0, tag.getInteger("CNPCPlusSpawnerProgress"));
        if (tag.hasKey("CNPCPlusSpawnerBatch")) this.cnpcplus$batchSpawned = tag.getBoolean("CNPCPlusSpawnerBatch");
        this.number = this.cnpcplus$progress;
        if (tag.hasKey("CNPCPlusSpawnerPending", 11)) {
            this.cnpcplus$batch = new ArrayDeque<>();
            for (int key : tag.getIntArray("CNPCPlusSpawnerPending")) this.cnpcplus$batch.add(key);
        }
        this.cnpcplus$recoveryUntil = ((JobSpawner) (Object) this).npc.world.getTotalWorldTime() + 40;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeEntries(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound out = cir.getReturnValue();
        out.setTag(SpawnerData.KEY, SpawnerData.write((JobSpawner) (Object) this, true));
        for (int i = 1; i <= 6; i++) out.removeTag("SpawnerNBT" + i);
        out.setInteger("CNPCPlusSpawnerProgress", this.cnpcplus$progress);
        out.setBoolean("CNPCPlusSpawnerBatch", this.cnpcplus$batchSpawned);
        out.setIntArray("CNPCPlusSpawnerPending", this.cnpcplus$batch == null ? new int[0]
                : this.cnpcplus$batch.stream().mapToInt(Integer::intValue).toArray());
    }

    @Inject(method = "cleanCompound", at = @At("TAIL"), require = 1)
    private void cnpcplus$stripEditorPayload(NBTTagCompound tag, CallbackInfo ci) {
        tag.removeTag(SpawnerData.KEY);
        tag.removeTag("CNPCPlusSpawnerProgress");
        tag.removeTag("CNPCPlusSpawnerBatch");
        tag.removeTag("CNPCPlusSpawnerPending");
    }

    @Inject(method = "getTitles", at = @At("RETURN"), require = 1)
    private void cnpcplus$entryTitles(CallbackInfoReturnable<NBTTagCompound> cir) {
        cir.getReturnValue().setTag(SpawnerData.TITLES, SpawnerData.write((JobSpawner) (Object) this, false));
    }

    @Inject(method = "setJobCompound", at = @At("TAIL"), require = 1)
    private void cnpcplus$acceptLegacySlot(int slot, NBTTagCompound tag, CallbackInfo ci) { SpawnerData.putInline((JobSpawner) (Object) this, slot, tag); }

    @Inject(method = "isEmpty()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$dynamicEmpty(CallbackInfoReturnable<Boolean> cir) { cir.setReturnValue(SpawnerData.get((JobSpawner) (Object) this).isEmpty()); }

    @Inject(method = "getCompound(I)Lnet/minecraft/nbt/NBTTagCompound;", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$dynamicCompound(int next, CallbackInfoReturnable<NBTTagCompound> cir) {
        NavigableMap<Integer, SpawnerData.Entry> entries = SpawnerData.get((JobSpawner) (Object) this);
        Integer key = entries.ceilingKey(Math.max(1, next));
        if (key == null) cir.setReturnValue(null);
        else { this.number = key; this.cnpcplus$progress = key; cir.setReturnValue(entries.get(key).resolve()); }
    }

    @Inject(method = "aiUpdateTask", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$spawnDynamicList(CallbackInfo ci) {
        JobSpawner job = (JobSpawner) (Object) this;
        if (job.npc.world.isRemote) return;
        ci.cancel();
        job.checkSpawns();
        NavigableMap<Integer, SpawnerData.Entry> entries = SpawnerData.get(job);
        if (entries.isEmpty()) return;
        if (job.spawnType == 1) {
            if (this.cnpcplus$batch == null || this.cnpcplus$batch.isEmpty()) {
                if (!job.spawned.isEmpty()) return;
                if (this.cnpcplus$batchSpawned && !job.doesntDie) { cnpcplus$die(job); return; }
                this.cnpcplus$batch = new ArrayDeque<>(entries.keySet());
                this.number = entries.lastKey();
                this.cnpcplus$progress = this.number;
            }
            // 大目录分批处理，避免数百次实体创建都挤在单个 tick。
            for (int budget = 8; budget > 0 && !this.cnpcplus$batch.isEmpty(); budget--) {
                SpawnerData.Entry entry = entries.get(this.cnpcplus$batch.removeFirst());
                if (entry != null && this.spawnEntity(entry.resolve()) != null) this.cnpcplus$batchSpawned = true;
            }
            return;
        }
        if (!job.spawned.isEmpty()) return;
        Integer key;
        if (job.spawnType == 2) {
            List<Integer> keys = new ArrayList<>(entries.keySet());
            key = keys.get(job.npc.getRNG().nextInt(keys.size()));
        } else {
            key = entries.higherKey(this.cnpcplus$progress);
            if (key == null && job.doesntDie) key = entries.firstKey();
        }
        if (key == null) { cnpcplus$die(job); return; }
        this.number = key;
        this.cnpcplus$progress = key;
        this.spawnEntity(entries.get(key).resolve());
    }

    @Inject(method = "aiShouldExecute", at = @At("HEAD"), require = 1)
    private void cnpcplus$recoverSpawned(CallbackInfoReturnable<Boolean> cir) {
        JobSpawner job = (JobSpawner) (Object) this;
        if (!job.npc.world.isRemote && job.spawned.isEmpty() && job.npc.world.getTotalWorldTime() <= this.cnpcplus$recoveryUntil) {
            job.spawned = this.getNearbySpawned();
        }
    }

    @Inject(method = "aiShouldExecute", at = @At("RETURN"), require = 1)
    private void cnpcplus$completedBatchWhenIdle(CallbackInfoReturnable<Boolean> cir) {
        JobSpawner job = (JobSpawner) (Object) this;
        if (!job.npc.world.isRemote && job.spawnType == 1 && this.cnpcplus$batchSpawned && !job.doesntDie
                && (this.cnpcplus$batch == null || this.cnpcplus$batch.isEmpty()) && job.spawned.isEmpty()
                && job.npc.world.getTotalWorldTime() > this.cnpcplus$recoveryUntil
                && !job.npc.isKilled() && job.npc.isEntityAlive()) cnpcplus$die(job);
    }

    @Inject(method = {"reset", "aiStartExecuting"}, at = @At("TAIL"), require = 1)
    private void cnpcplus$keepProgress(CallbackInfo ci) {
        this.cnpcplus$progress = Math.max(this.number, this.cnpcplus$progress);
        this.number = this.cnpcplus$progress;
    }

    @Inject(method = "killed", at = @At("HEAD"), require = 1)
    private void cnpcplus$resetLife(CallbackInfo ci) { this.cnpcplus$progress = 0; this.number = 0; this.cnpcplus$batchSpawned = false; this.cnpcplus$batch = null; }

    @Unique private static void cnpcplus$die(JobSpawner job) {
        if (!job.doesntDie && !job.npc.isKilled()) { RespawnCycleStore.forceRespawn(job.npc); job.npc.func_70106_y(); }
    }
}
