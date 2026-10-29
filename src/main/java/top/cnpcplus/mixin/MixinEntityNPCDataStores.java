package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.persist.NpcDataExternalStore;

/**
 * 召唤师 SpawnerData 与掉落物（NpcInv+DropChance）外置分离（240 #2/#3）。
 *
 * <p>读档：挂 m_7378_ 内 {@code DataScript.load} 的 INVOKE——super 已读 UUID
 * （owner 正确）、原生 {@code job.load}(1079)/{@code inventory.load}(1080)/
 * transform 基线(1081) 尚未跑，贴键即被原生 load 读走（零 reload）；linked
 * {@code loadNpcData}(1086) 在其后仍按原生顺序覆盖（linked 权威不变）。
 * 客户端守卫在 store 内（isClientSide 跳过），故 GUI 克隆捕获的客户端 compound
 * 永远保留内联全键。
 *
 * <p>存档：m_7380_ RETURN，原生 job.save/inventory.save 已把数据键写进 tag，
 * 外置落盘后摘除（ID/STATE 指针键保留，几字节）。linked 共享快照走
 * readNpcData（各组件直调 save，不经 m_7380_）、客户端同步走 writeSpawnData
 * （memory 直写），均不受摘除影响。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCDataStores {

    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/data/DataScript;load(Lnet/minecraft/nbt/CompoundTag;)V"), require = 1, remap = false)
    private void cnpcplus$attachExternalData(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        NpcDataExternalStore.SPAWNER.prepareLoad(npc, tag);
        NpcDataExternalStore.INVENTORY.prepareLoad(npc, tag);
    }

    @Inject(method = "addAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), require = 1, remap = false)
    private void cnpcplus$stripExternalData(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        NpcDataExternalStore.SPAWNER.saveNpc(npc, tag);
        NpcDataExternalStore.INVENTORY.saveNpc(npc, tag);
    }
}
