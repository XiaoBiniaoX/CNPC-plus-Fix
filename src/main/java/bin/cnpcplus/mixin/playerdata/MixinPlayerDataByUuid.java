package bin.cnpcplus.mixin.playerdata;

import bin.cnpcplus.playerdata.PlayerDataStore;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把服务端 PlayerData 的缓存键从实体 ID 换成玩家 UUID。
 *
 * <p>CNPC 1.21.1 的 {@code dataMap} 以 {@code player.getId()} 为键且从不清理，
 * 实体 ID 在玩家退出后会被复用，导致新玩家拿到旧玩家的数据、自己的存档不被读取，
 * 表现为「玩家每次进出，任务等数据被重置」。详见 PlayerDataStore 的说明。
 *
 * <p>只接管服务端分支；客户端仍走原版的 {@code CustomNpcs.proxy.getPlayerData}。
 */
@Mixin(value = PlayerData.class, remap = false)
public class MixinPlayerDataByUuid {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$getByUuid(Player player, CallbackInfoReturnable<PlayerData> cir) {
        if (player == null || player.level().isClientSide) return;
        cir.setReturnValue(PlayerDataStore.get(player));
    }
}
