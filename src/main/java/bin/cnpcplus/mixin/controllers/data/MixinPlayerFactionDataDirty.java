package bin.cnpcplus.mixin.controllers.data;

import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerFactionData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerFactionData.increasePoints 后置 updateClient，修复阵营点数变更不同步。
 *
 * <p>CNPC 原版 increasePoints 只改内存不置脏标记，客户端阵营点数落后于服务端。
 */
@Mixin(value = PlayerFactionData.class, remap = false)
public class MixinPlayerFactionDataDirty {

    @Inject(method = "increasePoints", at = @At("TAIL"), remap = false)
    private void cnpcplus$dirtyAfterIncrease(Player player, int factionId, int points, CallbackInfo ci) {
        if (player != null && !player.level().isClientSide) {
            PlayerData.get(player).updateClient = true;
        }
    }
}
