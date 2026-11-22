package top.cnpcplus.mixin;

import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketRemoteFreeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.wand.WandFreezeState;
import top.cnpcplus.wand.network.WandNpcPacket;

@Mixin(value = SPacketRemoteFreeze.class, remap = false)
public abstract class MixinRemoteFreezeAll extends PacketServerBasic {
    @Inject(method = "handle", at = @At("TAIL"), require = 1)
    private void cnpcplus$takeOverIndividualFreezes(CallbackInfo ci) {
        WandFreezeState.get(player.server).clearIndividual();
        player.server.getPlayerList().getPlayers().forEach(WandNpcPacket::sendFreezeState);
    }
}
