package bin.cnpcplus.mixin.linked;

import bin.cnpcplus.linked.LinkedScriptSync;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.PacketHandlerServer;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketHandlerServer.class, remap = false)
public abstract class MixinPacketHandlerServerScriptSync {
    @Inject(method = "handlePacket", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveLinkedScript(EnumPacketServer type, ByteBuf buffer,
                                           EntityPlayerMP player, EntityNPCInterface npc,
                                           CallbackInfo ci) {
        if (type != EnumPacketServer.ScriptDataSave || npc == null || !LinkedScriptSync.isEnabled(npc.linkedData)) {
            return;
        }
        LinkedScriptSync.save(npc, npc.linkedData);
        LinkedNpcController.Instance.save();
    }
}
