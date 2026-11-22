package top.cnpcplus.smithing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.smithing.SmithingRecipeManager;
import top.cnpcplus.smithing.SmithingRecipeRegistry;

import java.util.function.Supplier;

/** 客户端→服务端：删除自定义锻造配方（227 从 PacketSmeltingRemove 复制，保存路径同为热添加）。 */
public class PacketSmithingRemove {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final int id;

    public PacketSmithingRemove(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketSmithingRemove decode(FriendlyByteBuf buf) { return new PacketSmithingRemove(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.debug("[smithing] Remove拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            if (!SmithingRecipeRegistry.remove(this.id)) {
                LOGGER.debug("[smithing] Remove终止: Registry无此id={}", this.id);
                return;
            }
            SmithingRecipeManager.publishChange(sender.server, this.id, null);
        });
        ctx.get().setPacketHandled(true);
    }
}
