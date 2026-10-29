package top.cnpcplus.anvil.network;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.cnpcplus.anvil.AnvilRecipeRegistry;

/** 客户端→服务端：请求同步配方列表（阶段231 从 PacketSmithingRequestList 复制）。 */
public record PacketAnvilRequestList() implements CustomPacketPayload {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");
    public static final Type<PacketAnvilRequestList> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "anvil_request_list"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAnvilRequestList> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {}, buf -> new PacketAnvilRequestList());

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PacketAnvilRequestList packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 需要权限：配方列表只给编辑者看。否则任意客户端都能反复索取全部配方 NBT，
            // 既泄露配方内容，也能靠服务端序列化做流量放大。
            if (!(context.player() instanceof ServerPlayer sender) || !sender.hasPermissions(2)) {
                LOGGER.debug("[anvil] RequestList拒绝: 无发送者或权限不足 player={}",
                        context.player() == null ? "?" : context.player().getGameProfile().getName());
                return;
            }
            AnvilRecipeRegistry.syncToPlayers(sender.server, sender);
        });
    }
}
