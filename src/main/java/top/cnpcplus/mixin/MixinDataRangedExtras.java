package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ranged.RangedBuffStore;
import top.cnpcplus.ranged.RangedFxStore;

/**
 * 把弹射物的 BUFF 表与粒子特效表挂进 {@code DataRanged} 的 NBT（234），
 * 白拿存档与两端同步——与近战 {@code MixinDataMeleeBuffNBT} 完全同套路。
 *
 * <h3>为什么不需要新增网络包</h3>
 * {@code DataStats.readToNBT/save}（反编译 57-58 / 77-78 行）把 {@code ranged}
 * <b>直接展开到 NPC 根标签</b>（无子标签包裹）；GUI 保存走
 * {@code SPacketMenuSave(STATS)} → 服务端 {@code npc.stats.readToNBT}，
 * 读取走 {@code SPacketMenuGet(STATS)}。两键挂在 DataRanged 复合标签上即自动：
 * GUI 保存过河、GUI 打开回传、随 NPC 实体 NBT 进存档。
 *
 * <h3>注入点签名</h3>
 * {@code DataRanged.save} 返回 {@code CompoundTag}（反编译 78 行）→
 * {@code CallbackInfoReturnable} + {@code cir.getReturnValue()} 取返回标签；
 * {@code load} 返回 void（:47）→ {@code CallbackInfo}。
 *
 * <h3>两键</h3>
 * {@code CNPCPlusRangedBuffs}（多 BUFF，与近战 CNPCPlusMeleeBuffs 平行）、
 * {@code CNPCPlusProjectileFx}（粒子尾迹特效）——均带 CNPCPlus 前缀，
 * 与原版平铺键 {@code pEffect/pTrail} 无冲突。
 */
@Mixin(value = DataRanged.class, remap = false)
public class MixinDataRangedExtras {

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpcplus$readExtras(CompoundTag compound, CallbackInfo ci) {
        DataRanged self = (DataRanged) (Object) this;
        RangedBuffStore.read(self, compound);
        RangedFxStore.read(self, compound);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeExtras(CompoundTag compound,
                                      CallbackInfoReturnable<CompoundTag> cir) {
        DataRanged self = (DataRanged) (Object) this;
        CompoundTag out = cir.getReturnValue();
        CompoundTag target = out == null ? compound : out;
        RangedBuffStore.write(self, target);
        RangedFxStore.write(self, target);
    }
}
