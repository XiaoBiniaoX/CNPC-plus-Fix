package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntityLiving;
import noppes.npcs.controllers.data.CloneSpawnData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A1: 召唤物全死 → 本体应正常死亡（可复活），而非 discard（消失无法复活）。
 * A3: "全部召唤"(spawnType==1) 数量翻倍 —— 原版 spawnEntity(0..5) 经 getCompound 重复命中同一高槽。
 *     修复：spawnType==1 时直接按 key 精确取 data 中该槽的 CloneSpawnData，各召 1 次。
 */
@Mixin(value = JobSpawner.class, remap = false)
public class MixinJobSpawnerFix {

    /**
     * A1: aiUpdateTask 中三处对 npc 的 discard(m_146870_) 全部改为正常死亡流程。
     * 原版"召唤物全死则本体消失(discard)"→ 应触发 EntityNPCInterface 死亡/重生逻辑(remove(KILLED))。
     */
    @Redirect(method = "aiUpdateTask", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/EntityNPCInterface;m_146870_()V"))
    private void cnpcplus$dieInsteadOfDiscard(EntityNPCInterface npc) {
        npc.remove(Entity.RemovalReason.KILLED);
    }

    /**
     * A3: 拦截 spawnEntity(int)。当 spawnType==1（全部召唤）时，仅当 data 中确有该 key 才召唤，
     * 并且每个 key 各召 1 次（不再经 getCompound 的 key>=i 语义重复命中）。
     */
    @Inject(method = "spawnEntity(I)Lnoppes/npcs/api/entity/IEntityLiving;", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$spawnOncePerSlot(int slot, CallbackInfoReturnable<IEntityLiving> cir) {
        JobSpawner self = (JobSpawner) (Object) this;
        if (self.spawnType != 1) return;
        CloneSpawnData sd = self.data.get(slot);
        if (sd == null) {
            cir.setReturnValue(null);
            return;
        }
        net.minecraft.nbt.CompoundTag compound = sd.getCompound();
        if (compound == null || !compound.contains("id")) {
            cir.setReturnValue(null);
            return;
        }
        LivingEntity living = ((JobSpawnerInvoker) this).cnpcplus$spawnEntity(compound);
        if (living == null) {
            cir.setReturnValue(null);
            return;
        }
        cir.setReturnValue((IEntityLiving) NpcAPI.Instance().getIEntity(living));
    }
}
