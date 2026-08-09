package top.cnpcplus.trader.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.containers.ContainerNPCTraderSetup;
import top.cnpcplus.trader.TraderPager;

import java.util.function.Supplier;

public class PacketTraderPage {
    private final int page;
    private final boolean delete;

    public PacketTraderPage(int page, boolean delete) {
        this.page = page;
        this.delete = delete;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(this.page);
        buf.writeBoolean(this.delete);
    }

    public static PacketTraderPage decode(FriendlyByteBuf buf) {
        return new PacketTraderPage(buf.readInt(), buf.readBoolean());
    }

    public static void handle(PacketTraderPage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.containerMenu instanceof ContainerNPCTraderSetup menu) {
                if (msg.delete) {
                    if (TraderPager.removePage(menu.role, msg.page)) {
                        menu.role.toSave = true;
                    }
                } else {
                    if (msg.page > 0 && msg.page >= TraderPager.getPageCount(menu.role)) {
                        TraderPager.addPage(menu.role);
                    }
                    TraderPager.switchPage(menu.role, msg.page);
                }
                menu.broadcastChanges();
            } else if (player.containerMenu instanceof ContainerNPCTrader menu) {
                TraderPager.switchPage(menu.role, msg.page);
                menu.broadcastChanges();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
