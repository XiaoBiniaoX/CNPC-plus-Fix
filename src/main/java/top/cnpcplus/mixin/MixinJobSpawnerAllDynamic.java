package top.cnpcplus.mixin;

import java.util.TreeSet;
import bin.cnpcplus.spawner.SpawnDelayStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import noppes.npcs.controllers.data.CloneSpawnData;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 召唤师 type1「全部召唤」破除原版 6 槽硬编码 + 死亡门改批次标记（240 #1）。
 *
 * <p>原版 aiUpdateTask 的 type1 分支写死 {@code spawnEntity(0..5)} + {@code number=6}，
 * 第 6 槽以后永远召不出。本 mixin 在 HEAD 接管该分支（仅 spawnType==1 且 spawned 为空）：
 * 按 data 的 key 升序全数召唤（经 {@link JobSpawnerInvoker} 精确取每个 CloneSpawnData，
 * 保持来源「每槽各召一次」语义），number 记为 maxKey+1
 * 作为 NpcSpawnerNr 戳记（批次内所有子体同号）。
 *
 * <p>死亡门根因修复（用户实测「召唤物死后本体死亡无效，需关→开 toggle」）：原门用
 * {@code number > maxKey} 判「本批次已出全」，但 number 会被 stop→reset() 与 aiStartExecuting
 * 清零/按 NpcSpawnerNr 重扫归零（任务因 target 丢失停止后重启、或 spawned 已空时重扫），
 * 门失效后走重复召批或永不触发。改用 {@link #cnpcplus$batchSpawned}：仅在本 mixin 成功
 * 召出批次时置位，永不被 reset/stop 清除，仅 job.killed（本体死亡）清除——跨 stop/start 稳定，
 * 且防同实例复活（EntityNPCInterface.reset 经 job.reset 复用同一 JobSpawner）后死循环。
 *
 * <p>三道死亡门（条件一致，先到先触发），条件全满足且 {@code !doesntDie} → 本体
 * {@code remove(KILLED)}（正常死亡可复活）：
 * <ul>
 *   <li>aiUpdateTask HEAD：任务运行中，spawned 空且 SpawnDelayStore 冷却过 → 触发。</li>
 *   <li>aiShouldExecute RETURN：任务因无敌目标停着永不重启也能死——方法体内 1/30
 *       getNearbySpawned 重扫先跑（找回子体则不触发），checkSpawns 置的外部计时
 *       提供 spawnDelay 缓冲；canUse 每 tick 轮询，故门常驻。</li>
 *   <li>killed HEAD：本体死亡清 flag（正常死亡/被门杀死均走此），复活后全新批次。</li>
 * </ul>
 * cooldown 未到、非 type1、spawned 非空、配置为空一律放行原生
 * （type0/type2/checkSpawns 全部照旧）。
 */
@Mixin(value = JobSpawner.class, remap = false)
public class MixinJobSpawnerAllDynamic {

    @Shadow(remap = false)
    private int number;

    /** 本生命周期内已召出过 type1 批次。killed 时清零，reset/stop/aiStartExecuting 不动。 */
    @Unique
    private boolean cnpcplus$batchSpawned;

    @Inject(method = "aiUpdateTask", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$spawnAllConfigured(CallbackInfo ci) {
        JobSpawner self = (JobSpawner) (Object) this;
        if (self.npc == null || self.npc.level().isClientSide) return;
        if (self.spawnType != 1 || !self.spawned.isEmpty()) return;
        if (self.npc.level().getGameTime() < SpawnDelayStore.getNextTime(self)) return;

        TreeSet<Integer> keys = new TreeSet<>(self.data.keySet());
        if (keys.isEmpty()) return;

        if (this.cnpcplus$batchSpawned && !self.doesntDie) {
            self.npc.remove(Entity.RemovalReason.KILLED);
            ci.cancel();
            return;
        }

        this.number = keys.last() + 1;
        for (int key : keys) {
            CloneSpawnData sd = self.data.get(key);
            if (sd == null) continue;
            CompoundTag compound = sd.getCompound();
            if (compound == null || !compound.contains("id")) continue;
            ((JobSpawnerInvoker) this).cnpcplus$spawnEntity(compound);
        }
        this.cnpcplus$batchSpawned = !self.spawned.isEmpty();
        ci.cancel();
    }

    @Inject(method = "aiShouldExecute", at = @At("RETURN"))
    private void cnpcplus$deathGateWhenIdle(CallbackInfoReturnable<Boolean> cir) {
        JobSpawner self = (JobSpawner) (Object) this;
        if (!this.cnpcplus$batchSpawned || self.spawnType != 1 || self.doesntDie) return;
        if (!self.spawned.isEmpty() || self.data.isEmpty()) return;
        if (self.npc == null || self.npc.level().isClientSide) return;
        if (self.npc.level().getGameTime() < SpawnDelayStore.getNextTime(self)) return;
        if (self.npc.isKilled() || !self.npc.isAlive()) return;
        self.npc.remove(Entity.RemovalReason.KILLED);
    }

    @Inject(method = "killed", at = @At("HEAD"))
    private void cnpcplus$clearBatchSpawned(CallbackInfo ci) {
        this.cnpcplus$batchSpawned = false;
    }
}
