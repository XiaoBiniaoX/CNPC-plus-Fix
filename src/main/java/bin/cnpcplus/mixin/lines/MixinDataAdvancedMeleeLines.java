package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.MeleeLineStore;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把「近战打击」台词接入 DataAdvanced 原生的 NBT 通道。
 *
 * 保存链本来就是全量 NBT：
 *   GuiNPCLinesMenu.save() / GuiNPCLinesEdit.save()
 *     → EnumPacketServer.MainmenuAdvancedSave（带 NPC_ADVANCED 权限）
 *     → PacketHandlerServer: npc.advanced.readToNBT(...)
 * 读取链是 MainmenuAdvancedGet → writeToNBT → GUI_DATA。
 * 所以只要新字段进了 writeToNBT / readToNBT，存档与双向同步自动生效，
 * **无需新增任何网络包**，也不引入新的服务端入口。
 *
 * 复用原生 {@code Lines.writeToNBT()/readNBT()} 的格式
 * （NBTTagList of {Slot, Line, Song}），不自建结构。
 */
@Mixin(value = DataAdvanced.class, remap = false)
public class MixinDataAdvancedMeleeLines {

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$writeMeleeLines(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound result = cir.getReturnValue();
        if (result == null) {
            return;
        }
        // peek 而非 get：从未配置过近战台词的 NPC 不写这个键，保持存档干净。
        // 但一旦配置过（哪怕后来被清空）就必须写，否则「清空」无法持久化 ——
        // readToNBT 只在键存在时才应用，键缺失会让旧台词残留。
        Lines lines = MeleeLineStore.peek((DataAdvanced) (Object) this);
        if (lines == null) {
            return;
        }
        result.setTag(MeleeLineStore.NBT_KEY, lines.writeToNBT());
    }

    /**
     * 只在键存在时才应用，且**就地写入已有的 Lines 实例**。
     *
     * 两点都不能省：
     *  1. readToNBT 也被 GUI_DATA 之外的链路调用，无条件读取会把已有台词静默
     *     清空（本项目在 MountControl 上踩过同类坑，见 findings 阶段 22）。
     *  2. 必须复用同一个 Lines 对象，不能 new 一个替换掉。原版
     *     {@code Lines.readNBT} 就是就地改 {@code this.lines}，因为
     *     {@code GuiNPCLinesEdit} 在构造时就把 Lines 引用存了下来
     *     （{@code this.lines = lines}），而它的 {@code setGuiData} 会先调
     *     {@code advanced.readToNBT(...)} 再按自己那份引用重建界面。
     *     若在这里换了对象，GUI 读到的仍是旧实例，玩家的编辑会写进旧对象、
     *     而 writeToNBT 取的是新对象 —— 台词会凭空丢失。
     */
    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$readMeleeLines(NBTTagCompound compound, CallbackInfo ci) {
        if (compound == null || !compound.hasKey(MeleeLineStore.NBT_KEY)) {
            return;
        }
        Lines lines = MeleeLineStore.get((DataAdvanced) (Object) this);
        if (lines == null) {
            return;
        }
        lines.readNBT(compound.getCompoundTag(MeleeLineStore.NBT_KEY));
    }
}
