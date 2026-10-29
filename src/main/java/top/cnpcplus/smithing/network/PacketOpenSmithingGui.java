package top.cnpcplus.smithing.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.smithing.ContainerSmithingRecipes;

/** 客户端→服务端：请求打开自定义锻造配方编辑容器（226 从 PacketOpenSmeltingGui 复制）。 */
public record PacketOpenSmithingGui(int selectedId) implements CustomPacketPayload {
    public static final Type<PacketOpenSmithingGui> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "open_smithing_gui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenSmithingGui> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.selectedId), buf -> new PacketOpenSmithingGui(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketOpenSmithingGui packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            int id = packet.selectedId;
            player.openMenu(new SimpleMenuProvider((containerId, inv, p) ->
                            new ContainerSmithingRecipes(containerId, inv, id),
                    Component.translatable("cnpcplus.smithing.title")), buf -> buf.writeInt(id));
        });
    }
}
