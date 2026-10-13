package bin.cnpcplus.mixin.playerdata;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * getSyncNBT 补上 transportData / bankData / mailData。
 *
 * <p>CNPC 原版 getSyncNBT 只含 dialog+quest+faction，导致传送点解锁、银行、邮件
 * 的变更不走 updateClient 同步，客户端数据落后于服务端（「页面回弹」根因之一）。
 */
@Mixin(value = PlayerData.class, remap = false)
public class MixinPlayerDataSyncNbt {

    @Inject(method = "getSyncNBT", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$addMissingData(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag compound = cir.getReturnValue();
        PlayerData self = (PlayerData) (Object) this;
        if (self.player != null) {
            try {
                self.bankData.saveNBTData(self.player.registryAccess(), compound);
            } catch (Exception ignored) {
            }
        }
        self.transportData.saveNBTData(compound);
        self.mailData.saveNBTData(compound);
    }
}
