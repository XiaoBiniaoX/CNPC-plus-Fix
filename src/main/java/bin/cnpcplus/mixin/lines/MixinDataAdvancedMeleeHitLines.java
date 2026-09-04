package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.MeleeHitLinesAccess;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 将 CNPCPlus 的近战打击台词附加到 DataAdvanced，并随 NPC 高级数据保存与读取。
 */
@Mixin(value = DataAdvanced.class, remap = false)
public class MixinDataAdvancedMeleeHitLines implements MeleeHitLinesAccess {

    @Unique
    private final Lines cnpcplus$meleeHitLines = new Lines();

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveMeleeHitLines(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        // 写入独立键，保留原版 DataAdvanced 的所有台词数据。
        cir.getReturnValue().put("CNPCPlusMeleeHitLines", this.cnpcplus$meleeHitLines.save());
    }

    @Inject(method = "readToNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$readMeleeHitLines(CompoundTag compound, CallbackInfo ci) {
        // 键缺失时 getCompound 返回空标签，Lines.readNBT 可安全读取旧存档。
        this.cnpcplus$meleeHitLines.readNBT(compound.getCompound("CNPCPlusMeleeHitLines"));
    }

    @Override
    public Lines cnpcplus$getMeleeHitLines() {
        // 返回附加近战打击台词集合。
        return this.cnpcplus$meleeHitLines;
    }
}
