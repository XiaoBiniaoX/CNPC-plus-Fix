package bin.cnpcplus.linked.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.accessor.LinkedScriptSyncAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.controllers.LinkedNpcController;

public record PacketLinkedScriptSync(String linkedName, boolean scriptSync) implements CustomPacketPayload {
    public static final Type<PacketLinkedScriptSync> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "linked_script_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketLinkedScriptSync> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeUtf(msg.linkedName, Short.MAX_VALUE);
                        buf.writeBoolean(msg.scriptSync);
                    },
                    buf -> new PacketLinkedScriptSync(buf.readUtf(Short.MAX_VALUE), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketLinkedScriptSync msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer)) return;
            LinkedNpcController.LinkedData data = LinkedNpcController.Instance.getData(msg.linkedName);
            if (data == null) return;
            ((LinkedScriptSyncAccess)(Object)data).cnpcplus$setScriptSync(msg.scriptSync);
            LinkedNpcController.Instance.save();
            PacketDistributor.sendToPlayer((ServerPlayer)ctx.player(),
                    new PacketLinkedScriptSyncState(msg.linkedName, msg.scriptSync));
        });
    }
}