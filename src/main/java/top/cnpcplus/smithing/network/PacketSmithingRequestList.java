package top.cnpcplus.smithing.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.smithing.SmithingRecipeManager;

/** 客户端→服务端：请求同步配方列表（227 从 PacketSmeltingRequestList 复制）。 */
public record PacketSmithingRequestList() implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public static final Type<PacketSmithingRequestList> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "smithing_request_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSmithingRequestList> STREAM_CODEC =
            StreamCodec.unit(new PacketSmithingRequestList());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketSmithingRequestList packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sender)) return;
            // 需要权限：配方列表只给编辑者看。否则任意客户端都能反复索取全部配方 NBT，
            // 既泄露配方内容，也能靠服务端序列化做流量放大。
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.debug("[smithing] RequestList拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            SmithingRecipeManager.syncToPlayers(sender.server, sender);
        });
    }
}
