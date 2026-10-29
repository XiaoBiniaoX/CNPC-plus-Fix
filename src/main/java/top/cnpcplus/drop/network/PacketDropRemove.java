package top.cnpcplus.drop.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.drop.ContainerDropRecipes;
import top.cnpcplus.drop.DropRecipeRegistry;

/** 客户端→服务端：删除某个自定义掉落物配方（阶段232 从 PacketAnvilRemove 复制）。 */
public record PacketDropRemove(int id) implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public static final Type<PacketDropRemove> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "drop_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketDropRemove> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeVarInt(packet.id), buf -> new PacketDropRemove(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketDropRemove packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) {
                LOGGER.debug("[drop] Remove拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            if (!(sender.containerMenu instanceof ContainerDropRecipes)) return;
            if (!DropRecipeRegistry.remove(sender.server.registryAccess(), packet.id)) {
                LOGGER.debug("[drop] Remove拒绝: Registry删除失败 id={} player={}", packet.id,
                        sender.getGameProfile().getName());
                return;
            }
            LOGGER.debug("[drop] 配方删除 id={}", packet.id);
            DropRecipeRegistry.syncToPlayers(sender.server);
        });
    }
}
