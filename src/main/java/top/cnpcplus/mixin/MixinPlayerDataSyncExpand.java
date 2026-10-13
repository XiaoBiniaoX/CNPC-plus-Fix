package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S8（Medium）：getSyncNBT 只同步 3/7 子块（dialog/quest/faction）。
 * 补齐 bank / transport / mail / scriptStoreddata，客户端脚本读这些不再恒空。
 * 不含 timers（客户端计时器副作用），skin 有专用 PacketSyncSkin。
 */
@Mixin(value = PlayerData.class, remap = false)
public abstract class MixinPlayerDataSyncExpand {

    @Inject(method = "getSyncNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$expandSubBlocks(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag c = cir.getReturnValue();
        if (c == null) return;
        PlayerData self = (PlayerData) (Object) this;
        self.bankData.saveNBTData(c);
        self.transportData.saveNBTData(c);
        self.mailData.saveNBTData(c);
        c.put("ScriptStoreddata", self.scriptStoreddata);
    }
}
