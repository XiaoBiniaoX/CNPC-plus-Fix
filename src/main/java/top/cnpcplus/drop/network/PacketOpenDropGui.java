package top.cnpcplus.drop.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import top.cnpcplus.drop.ContainerDropRecipes;

import java.util.function.Supplier;

/** 客户端→服务端：请求打开自定义掉落物配方编辑容器（阶段232 从 PacketOpenAnvilGui 复制）。 */
public class PacketOpenDropGui {
    private final int selectedId;

    public PacketOpenDropGui(int selectedId) { this.selectedId = selectedId; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.selectedId); }

    public static PacketOpenDropGui decode(FriendlyByteBuf buf) { return new PacketOpenDropGui(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            int id = this.selectedId;
            NetworkHooks.openScreen(player, new SimpleMenuProvider((containerId, inv, p) ->
                            new ContainerDropRecipes(containerId, inv, id),
                    Component.translatable("cnpcplus.drop.title")), buf -> buf.writeInt(id));
        });
        ctx.get().setPacketHandled(true);
    }
}
