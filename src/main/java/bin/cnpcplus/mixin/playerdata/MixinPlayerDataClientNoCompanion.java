package bin.cnpcplus.mixin.playerdata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端 setNBT 跳过 PlayerCompanion，防止登录同步时本地生成幽灵随从实体。
 *
 * <p>CNPC getNBT 输出 PlayerCompanion（服务端保存需要），但客户端收到后
 * setNBT 会本地 addFreshEntity 生成幽灵随从。随从应由服务端控制生成。
 * 此处客户端分支移除 PlayerCompanion 键后再走原逻辑。
 */
@Mixin(value = PlayerData.class, remap = false)
public class MixinPlayerDataClientNoCompanion {

    @Inject(method = "setNBT", at = @At("HEAD"), remap = false)
    private void cnpcplus$stripCompanionOnClient(HolderLookup.Provider provider, CompoundTag data, CallbackInfo ci) {
        PlayerData self = (PlayerData) (Object) this;
        if (self.player != null && self.player.level().isClientSide) {
            data.remove("PlayerCompanion");
        }
    }
}
