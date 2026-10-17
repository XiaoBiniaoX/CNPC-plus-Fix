package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.NBTTags;
import noppes.npcs.controllers.data.DataTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 238-8：昼夜两外观切换时人形串台。
 *
 * <h3>根因</h3>
 * {@code DataTransform.transform()} 用 {@code NBTTags.NBTMerge(当前形态, 目标快照)}
 * 合并——NBTMerge 不能删键，而 {@code ModelDataShared.save:47-49} 条件写
 * {@code EntityName}（null 不写）→ 旧形态的模型键残留灌进目标形态（比如人形模型的
 * EntityName 留在非人形快照里），一切换就把错误模型键带上。
 *
 * <h3>修法</h3>
 * @Redirect transform() 内<b>仅 display 与 modelData 两处</b> NBTMerge
 * （字节码内出现顺序 ordinal=0/1，后续 stats/ai/inv 等段不动），
 * 直接返回目标快照（第二参数）——全量替换语义，根治全部条件键残留。
 */
@Mixin(value = DataTransform.class, remap = false)
public class MixinDataTransformFullReplace {

    @Redirect(method = "transform", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/NBTTags;NBTMerge(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            ordinal = 0), remap = false)
    private CompoundTag cnpcplus$fullReplaceDisplay(CompoundTag current, CompoundTag targetSnapshot) {
        return targetSnapshot;
    }

    @Redirect(method = "transform", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/NBTTags;NBTMerge(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            ordinal = 1), remap = false)
    private CompoundTag cnpcplus$fullReplaceModelData(CompoundTag currentModelData, CompoundTag targetModelData) {
        return targetModelData;
    }
}
