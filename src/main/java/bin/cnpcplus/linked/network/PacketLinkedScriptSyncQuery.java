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

public record PacketLinkedScriptSyncQuery(String linkedName) implements CustomPacketPayload {
    public static final Type<PacketLinkedScriptSyncQuery> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "linked_script_sync_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketLinkedScriptSyncQuery> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeUtf(msg.linkedName, Short.MAX_VALUE),
                    buf -> new PacketLinkedScriptSyncQuery(buf.readUtf(Short.MAX_VALUE))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketLinkedScriptSyncQuery msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            LinkedNpcController.LinkedData data = LinkedNpcController.Instance.getData(msg.linkedName);
            boolean state = data != null && ((LinkedScriptSyncAccess)(Object)data).cnpcplus$isScriptSync();
            PacketDistributor.sendToPlayer(sp, new PacketLinkedScriptSyncState(msg.linkedName, state));
        });
    }
}