package top.cnpcplus.drop.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.drop.DropRecipeRegistry;

/** 客户端→服务端：请求同步配方列表（阶段232 从 PacketAnvilRequestList 复制）。 */
public record PacketDropRequestList() implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public static final Type<PacketDropRequestList> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "drop_request_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketDropRequestList> STREAM_CODEC =
            StreamCodec.unit(new PacketDropRequestList());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketDropRequestList packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            // 需要权限：配方列表只给编辑者看。否则任意客户端都能反复索取全部配方 NBT，
            // 既泄露配方内容，也能靠服务端序列化做流量放大。
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.debug("[drop] RequestList拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            DropRecipeRegistry.syncToPlayers(sender.server, sender);
        });
    }
}
