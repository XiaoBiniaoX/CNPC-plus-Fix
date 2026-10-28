package bin.cnpcplus.mixin.follower;

import bin.cnpcplus.follower.FollowerHireLimit;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import noppes.npcs.NoppesUtilPlayer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleFollower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 238-10：新雇佣随从前检查玩家已拥有数量。 */
@Mixin(value = NoppesUtilPlayer.class, remap = false)
public class MixinNoppesUtilPlayerHireLimit {

    @Inject(method = "followerBuy(Lnoppes/npcs/roles/RoleFollower;Lnet/minecraft/inventory/IInventory;Lnet/minecraft/entity/player/EntityPlayerMP;Lnoppes/npcs/entity/EntityNPCInterface;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void cnpcplus$limitHire(RoleFollower role, IInventory currency, EntityPlayerMP player,
                                          EntityNPCInterface npc, CallbackInfo ci) {
        if (FollowerHireLimit.blockIfAtLimit(role, player)) {
            ci.cancel();
        }
    }
}
