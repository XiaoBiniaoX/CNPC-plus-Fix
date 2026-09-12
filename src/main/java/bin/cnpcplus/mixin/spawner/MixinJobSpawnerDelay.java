package bin.cnpcplus.mixin.spawner;

import bin.cnpcplus.spawner.SpawnDelayStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 补回召唤师职业的召唤冷却（CD）。
 *
 * <p>1.21.1 的 CNPC 把 1.20.1 原版的 `spawnDelay` / `nextSpawnTime` / `SpawnDelay` NBT
 * 整条链删掉了，所以召唤没有任何间隔。这里按 1.20.1 原版实现补回：
 * <ul>
 *   <li>NBT 键沿用原版的 `SpawnDelay`，旧存档与 1.20.1 存档都能直接读；</li>
 *   <li>召唤前检查 gameTime，未到时间直接取消本次召唤（等价 1.20.1 JobSpawner:106-108）；</li>
 *   <li>已召唤的全部消失后开始计时（等价 1.20.1 JobSpawner:148-150）。</li>
 * </ul>
 */
@Mixin(value = JobSpawner.class, remap = false)
public abstract class MixinJobSpawnerDelay {

    @Shadow
    public List<LivingEntity> spawned;

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveDelay(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        if (compound == null) return;
        compound.putInt("SpawnDelay", SpawnDelayStore.getDelay((JobSpawner) (Object) this));
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void cnpcplus$loadDelay(CompoundTag compound, CallbackInfo ci) {
        if (compound == null) return;
        // 键不存在时 getInt 返回 0，即「无冷却」，与原版默认值一致。
        SpawnDelayStore.setDelay((JobSpawner) (Object) this, compound.getInt("SpawnDelay"));
    }

    /**
     * 冷却未到时不召唤。
     *
     * <p>只在 `spawned.isEmpty()`（即真的准备召唤新的）这一支拦截，
     * 不能整个方法 HEAD 就取消，否则 `checkSpawns()` 那一支的存活检查和索敌也会被一起挡掉。
     */
    @Inject(method = "aiUpdateTask", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$checkCooldown(CallbackInfo ci) {
        JobSpawner self = (JobSpawner) (Object) this;
        if (self.npc == null || self.npc.level() == null) return;
        if (this.spawned == null || !this.spawned.isEmpty()) return;
        if (SpawnDelayStore.getDelay(self) <= 0) return;
        if (self.npc.level().getGameTime() < SpawnDelayStore.getNextTime(self)) {
            ci.cancel();
        }
    }

    /** 召唤物全部消失时开始计时，与 1.20.1 原版在 checkSpawns 末尾的处理一致。 */
    @Inject(method = "checkSpawns", at = @At("RETURN"))
    private void cnpcplus$startCooldown(CallbackInfo ci) {
        JobSpawner self = (JobSpawner) (Object) this;
        if (self.npc == null || self.npc.level() == null) return;
        if (this.spawned == null || !this.spawned.isEmpty()) return;
        int delay = SpawnDelayStore.getDelay(self);
        if (delay <= 0) return;
        SpawnDelayStore.setNextTime(self, self.npc.level().getGameTime() + delay);
    }
}
