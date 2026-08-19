package top.cnpcplus.smelting.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import top.cnpcplus.smelting.ContainerSmeltingRecipes;

import java.util.function.Supplier;

/** 客户端→服务端：请求打开自定义熔炼配方编辑容器。 */
public class PacketOpenSmeltingGui {
    private final int selectedId;

    public PacketOpenSmeltingGui(int selectedId) { this.selectedId = selectedId; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.selectedId); }

    public static PacketOpenSmeltingGui decode(FriendlyByteBuf buf) { return new PacketOpenSmeltingGui(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            int id = this.selectedId;
            NetworkHooks.openScreen(player, new SimpleMenuProvider((containerId, inv, p) ->
                            new ContainerSmeltingRecipes(containerId, inv, id),
                    Component.translatable("cnpcplus.smelting.title")), buf -> buf.writeInt(id));
        });
        ctx.get().setPacketHandled(true);
    }
}
