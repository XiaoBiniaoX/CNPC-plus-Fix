package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.lines.MeleeLinesStorage;

/**
 * 给 DataAdvanced 增加第七类台词：近战打击（NPC 打中目标时播报）。
 *
 * <p>原版六类台词分别是 interact/attack/world/killed/kill/npcInteract，
 * 其中 attackLines 是在 {@code EntityNPCInterface.m_6710_}（选定攻击目标）时触发的，
 * **一次目标切换只说一次**，与「每次打中」语义完全不同，所以必须新增一类。
 *
 * <p>NBT 键 {@code CNPCPlusMeleeLines}，结构与原版六类完全一致（Lines.save() 的
 * ListTag 格式），复用原版的 readNBT/save，不发明新格式。
 *
 * <p>向后兼容：读档时键不存在就当空台词，旧存档不受影响；存档时若一条台词都没配就
 * 不写这个键，旧版本读到的 NBT 与原版逐位相同。
 */
@Mixin(value = DataAdvanced.class, remap = false)
public class MixinDataAdvancedMeleeLines {

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveMeleeLines(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag out = cir.getReturnValue();
        if (out == null) return;
        Lines lines = MeleeLinesStorage.peek(this);
        // 一条都没配就不写键：保持存档与原版一致，避免给每个 NPC 都塞空标签。
        if (lines == null || lines.isEmpty()) return;
        out.put("CNPCPlusMeleeLines", lines.save());
    }

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readMeleeLines(CompoundTag compound, CallbackInfo ci) {
        // 无论有没有键都要走一遍 readNBT：Lines.readNBT 会先 clear，
        // 这样切换 NPC / 重复读档时不会残留上一份台词。
        MeleeLinesStorage.get(this).readNBT(compound.getCompound("CNPCPlusMeleeLines"));
    }
}
