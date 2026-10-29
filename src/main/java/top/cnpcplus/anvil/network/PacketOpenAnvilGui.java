package top.cnpcplus.anvil.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.anvil.ContainerAnvilRecipes;

/** 客户端→服务端：请求打开自定义铁砧配方编辑容器（阶段231 从 PacketOpenSmithingGui 复制）。 */
public record PacketOpenAnvilGui(int selectedId) implements CustomPacketPayload {
    public static final Type<PacketOpenAnvilGui> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "anvil_open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenAnvilGui> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.selectedId),
            buf -> new PacketOpenAnvilGui(buf.readVarInt()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketOpenAnvilGui packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            player.openMenu(new SimpleMenuProvider((containerId, inv, p) ->
                            new ContainerAnvilRecipes(containerId, inv, packet.selectedId),
                    Component.translatable("cnpcplus.anvil.title")),
                    buf -> buf.writeVarInt(packet.selectedId));
        });
    }
}
