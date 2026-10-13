package bin.cnpcplus.mixin.playerdata;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerQuestData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerQuestData.loadNBTData 键缺失保护。
 *
 * <p>CNPC 原版 getList 键不存在返回空 ListTag（非 null），导致 NBT 缺少 QuestData 键时
 * finishedQuests/activeQuests 被空 map 覆盖（进度全丢）。此处键不存在时 cancel 保留现有数据。
 */
@Mixin(value = PlayerQuestData.class, remap = false)
public class MixinPlayerQuestDataKeyGuard {

    @Inject(method = "loadNBTData", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$guardMissingKey(CompoundTag mainCompound, CallbackInfo ci) {
        if (mainCompound == null) return;
        if (!mainCompound.contains("QuestData")) {
            ci.cancel();
        }
    }
}
