package top.cnpcplus.drop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.drop.DropRecipeRegistry;

import java.util.function.Supplier;

/** 客户端→服务端：删除某个自定义掉落物配方（阶段232 从 PacketAnvilRemove 复制）。 */
public class PacketDropRemove {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final int id;

    public PacketDropRemove(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketDropRemove decode(FriendlyByteBuf buf) { return new PacketDropRemove(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.warn("[drop] Remove拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            if (!DropRecipeRegistry.remove(this.id)) {
                LOGGER.warn("[drop] Remove拒绝: Registry删除失败 id={} player={}", this.id,
                        sender.getGameProfile().getName());
                return;
            }
            LOGGER.info("[drop] 配方删除 id={}", this.id);
            DropRecipeRegistry.syncToPlayers(sender.server);
        });
        ctx.get().setPacketHandled(true);
    }
}
