package bin.cnpcplus.linked.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PacketLinkedScriptSyncState(String linkedName, boolean scriptSync) implements CustomPacketPayload {
    public static final Type<PacketLinkedScriptSyncState> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "linked_script_sync_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketLinkedScriptSyncState> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeUtf(msg.linkedName, Short.MAX_VALUE);
                        buf.writeBoolean(msg.scriptSync);
                    },
                    buf -> new PacketLinkedScriptSyncState(buf.readUtf(Short.MAX_VALUE), buf.readBoolean())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketLinkedScriptSyncState msg, IPayloadContext ctx) {
        if (ctx.flow().isClientbound()) {
            bin.cnpcplus.linked.client.PacketLinkedScriptSyncStateClient.handle(msg, ctx);
        }
    }
}
