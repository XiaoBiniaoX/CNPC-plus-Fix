package top.cnpcplus.drop.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.drop.ContainerDropRecipes;

/** 客户端→服务端：请求打开自定义掉落物配方编辑容器（阶段232 从 PacketOpenAnvilGui 复制）。 */
public record PacketOpenDropGui(int selectedId) implements CustomPacketPayload {
    public static final Type<PacketOpenDropGui> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "open_drop_gui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenDropGui> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.selectedId), buf -> new PacketOpenDropGui(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketOpenDropGui packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            int id = packet.selectedId;
            player.openMenu(new SimpleMenuProvider((containerId, inv, p) ->
                            new ContainerDropRecipes(containerId, inv, id),
                    Component.translatable("cnpcplus.drop.title")), buf -> buf.writeInt(id));
        });
    }
}
