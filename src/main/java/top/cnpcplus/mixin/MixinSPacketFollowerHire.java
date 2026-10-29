package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketFollowerHire;
import noppes.npcs.roles.RoleFollower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.follower.FollowerHireLimit;

@Mixin(value = SPacketFollowerHire.class, remap = false)
public class MixinSPacketFollowerHire {
    @Inject(method = "followerBuy", at = @At("HEAD"), cancellable = true)
    private static void cnpcplus$limitHire(RoleFollower role, Container currency, ServerPlayer player,
                                           EntityNPCInterface npc, CallbackInfo ci) {
        if (FollowerHireLimit.blockIfAtLimit(role, player)) {
            ci.cancel();
        }
    }
}
