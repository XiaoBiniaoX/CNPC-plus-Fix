package bin.cnpcplus.mixin;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
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
        ServerEntity serverEntity = null;
        if (entity.level() instanceof ServerLevel serverLevel) {
            ChunkMapMixin chunkMap = (ChunkMapMixin) ((ServerChunkCacheMixin) serverLevel.getChunkSource()).chunkMap();
            Object tracked = chunkMap.entityMap().get(entity.getId());
            if (tracked != null) {
                serverEntity = ((TrackedEntityMixin) tracked).serverEntity();
            }
        }
        try {
            java.lang.reflect.Field pktField = PacketNpcVisibleTrue.class.getDeclaredField("pkt");
            pktField.setAccessible(true);
            pktField.set(self, new ClientboundAddEntityPacket(entity, serverEntity, 0));
        } catch (Exception ignored) {
        }
    }
}
