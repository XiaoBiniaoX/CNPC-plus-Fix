package top.cnpcplus.anvil.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import top.cnpcplus.anvil.AnvilRecipeRegistry;

import java.util.function.Supplier;

/** 客户端→服务端：删除自定义铁砧配方（阶段231 从 PacketSmithingRemove 复制）。 */
public class PacketAnvilRemove {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private final int id;

    public PacketAnvilRemove(int id) { this.id = id; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.id); }

    public static PacketAnvilRemove decode(FriendlyByteBuf buf) { return new PacketAnvilRemove(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null || !sender.hasPermissions(2)) {
                LOGGER.debug("[anvil] Remove拒绝: 无发送者或权限不足 player={}",
                        sender == null ? "?" : sender.getGameProfile().getName());
                return;
            }
            if (!AnvilRecipeRegistry.remove(this.id)) {
                LOGGER.debug("[anvil] Remove终止: Registry无此id={}", this.id);
                return;
            }
            // 233d-UI9 日志清理：info→debug（默认不输出，只留 error 级）
            LOGGER.debug("[anvil] 配方删除 id={}", this.id);
            // 一人删除全服生效：全员同步（配方生效在服务端事件直查 Registry，删了自然查不到）
            AnvilRecipeRegistry.syncToPlayers(sender.server);
        });
        ctx.get().setPacketHandled(true);
    }
}
