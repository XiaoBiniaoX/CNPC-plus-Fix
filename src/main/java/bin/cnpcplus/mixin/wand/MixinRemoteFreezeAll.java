package bin.cnpcplus.mixin.wand;

import bin.cnpcplus.wand.WandFreezeState;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.PacketHandlerServer;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketHandlerServer.class, remap = false)
public class MixinRemoteFreezeAll {
    @Inject(method = "handlePacket", at = @At("RETURN"), require = 1)
    private void cnpcplus$clearIndividual(EnumPacketServer type, ByteBuf buf, EntityPlayerMP player, EntityNPCInterface npc, CallbackInfo ci) {
        if (type == EnumPacketServer.RemoteFreeze) WandFreezeState.get(player.getServer()).clearIndividual();
    }
}
