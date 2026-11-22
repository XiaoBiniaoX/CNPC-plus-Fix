package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.packets.server.SPacketRemoteNpcsGet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.wand.network.WandNpcPacket;

@Mixin(value = SPacketRemoteNpcsGet.class, remap = false)
public class MixinRemoteNpcFreezeState {
    @Inject(method = "sendNearbyNpcs", at = @At("TAIL"), require = 1)
    private static void cnpcplus$sendFreezeState(ServerPlayer player, CallbackInfo ci) {
        WandNpcPacket.sendFreezeState(player);
    }
}
