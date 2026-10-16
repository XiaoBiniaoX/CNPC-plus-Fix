package top.cnpcplus.smithing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smithing.SmithingRecipeManager;

import java.util.function.Supplier;

/** 客户端→服务端：请求同步配方列表（227 从 PacketSmeltingRequestList 复制）。 */
public class PacketSmithingRequestList {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public PacketSmithingRequestList() {}

    public void encode(FriendlyByteBuf buf) {}

    public static PacketSmithingRequestList decode(FriendlyByteBuf buf) { return new PacketSmithingRequestList(); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            // 需要权限：配方列表只给编辑者看。否则任意客户端都能反复索取全部配方 NBT，
            // 既泄露配方内容，也能靠服务端序列化做流量放大。
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.warn("[smithing] RequestList拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            SmithingRecipeManager.syncToPlayers(sender.server, sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
