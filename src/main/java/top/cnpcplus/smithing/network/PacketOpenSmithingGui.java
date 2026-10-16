package top.cnpcplus.smithing.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import top.cnpcplus.smithing.ContainerSmithingRecipes;

import java.util.function.Supplier;

/** 客户端→服务端：请求打开自定义锻造配方编辑容器（226 从 PacketOpenSmeltingGui 复制）。 */
public class PacketOpenSmithingGui {
    private final int selectedId;

    public PacketOpenSmithingGui(int selectedId) { this.selectedId = selectedId; }

    public void encode(FriendlyByteBuf buf) { buf.writeInt(this.selectedId); }

    public static PacketOpenSmithingGui decode(FriendlyByteBuf buf) { return new PacketOpenSmithingGui(buf.readInt()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            int id = this.selectedId;
            NetworkHooks.openScreen(player, new SimpleMenuProvider((containerId, inv, p) ->
                            new ContainerSmithingRecipes(containerId, inv, id),
                    Component.translatable("cnpcplus.smithing.title")), buf -> buf.writeInt(id));
        });
        ctx.get().setPacketHandled(true);
    }
}
