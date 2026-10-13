package bin.cnpcplus.mixin.playerdata;

import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * getNBT 不输出 PlayerCompanion，防止登录同步时客户端本地生成幽灵随从实体。
 *
 * <p>CNPC getNBT 行 157-161：hasCompanion 时 put PlayerCompanion。
 * 登录时 SyncController.syncPlayer 发 getNBT → 客户端收到后本地 addFreshEntity 生成幽灵随从。
 * 随从实体应由服务端控制生成，不应通过同步包传递。
 */
@Mixin(value = PlayerData.class, remap = false)
public class MixinPlayerDataNoCompanionSync {

    @Redirect(
        method = "getNBT",
        at = @At(value = "INVOKE", target = "Lnoppes/npcs/controllers/data/PlayerData;hasCompanion()Z"),
        remap = false
    )
    private boolean cnpcplus$skipCompanion(PlayerData instance) {
        return false; // 永不输出 PlayerCompanion 到 NBT
    }
}
