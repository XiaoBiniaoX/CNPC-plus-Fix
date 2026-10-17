package top.cnpcplus.anvil.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.anvil.AnvilRecipeRegistry;

import java.util.function.Supplier;

/** 客户端→服务端：请求同步配方列表（阶段231 从 PacketSmithingRequestList 复制）。 */
public class PacketAnvilRequestList {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    public PacketAnvilRequestList() {}

    public void encode(FriendlyByteBuf buf) {}

    public static PacketAnvilRequestList decode(FriendlyByteBuf buf) { return new PacketAnvilRequestList(); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            // 需要权限：配方列表只给编辑者看。否则任意客户端都能反复索取全部配方 NBT，
            // 既泄露配方内容，也能靠服务端序列化做流量放大。
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.debug("[anvil] RequestList拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            AnvilRecipeRegistry.syncToPlayers(sender.server, sender);
        });
        ctx.get().setPacketHandled(true);
    }
}
