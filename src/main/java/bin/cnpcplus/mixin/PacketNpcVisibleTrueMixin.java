package bin.cnpcplus.mixin;

import bin.cnpcplus.util.ServerEntityHelper;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import noppes.npcs.packets.client.PacketNpcVisibleTrue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketNpcVisibleTrue.class)
public class PacketNpcVisibleTrueMixin {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cnpcplus$fixVisibleEntityPos(Entity entity, CallbackInfo ci) {
        PacketNpcVisibleTrue self = (PacketNpcVisibleTrue)(Object)this;
        try {
            java.lang.reflect.Field pktField = PacketNpcVisibleTrue.class.getDeclaredField("pkt");
            pktField.setAccessible(true);
            pktField.set(self, new ClientboundAddEntityPacket(entity, ServerEntityHelper.getServerEntity(entity), 0));
        } catch (Exception ignored) {
        }
    }
}