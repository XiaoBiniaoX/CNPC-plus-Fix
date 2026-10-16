package top.cnpcplus.anvil.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import top.cnpcplus.anvil.ContainerAnvilRecipes;

import java.util.function.Supplier;

/** 客户端→服务端：请求打开自定义铁砧配方编辑容器（阶段231 从 PacketOpenSmithingGui 复制）。 */
public class PacketOpenAnvilGui {
    private final int selectedId;

    public PacketOpenAnvilGui(int selectedId) { this.selectedId = selectedId; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.selectedId); }

    public static PacketOpenAnvilGui decode(FriendlyByteBuf buf) { return new PacketOpenAnvilGui(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            int id = this.selectedId;
            NetworkHooks.openScreen(player, new SimpleMenuProvider((containerId, inv, p) ->
                            new ContainerAnvilRecipes(containerId, inv, id),
                    Component.translatable("cnpcplus.anvil.title")), buf -> buf.writeInt(id));
        });
        ctx.get().setPacketHandled(true);
    }
}
