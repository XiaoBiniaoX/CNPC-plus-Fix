package bin.cnpcplus.trader.network;

import bin.cnpcplus.trader.TraderPager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.containers.ContainerNPCTraderSetup;

public class PacketTraderPage implements IMessage {
    private int page;
    private boolean delete;

    public PacketTraderPage() {}

    public PacketTraderPage(int page, boolean delete) {
        this.page = page;
        this.delete = delete;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        page = buf.readInt();
        delete = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(page);
        buf.writeBoolean(delete);
    }

    public static class Handler implements IMessageHandler<PacketTraderPage, IMessage> {
        @Override
        public IMessage onMessage(final PacketTraderPage msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Container menu = player.openContainer;
                    if (menu instanceof ContainerNPCTraderSetup) {
                        ContainerNPCTraderSetup setup = (ContainerNPCTraderSetup) menu;
                        if (msg.delete) {
                            if (TraderPager.removePage(setup.role, msg.page)) {
                                setup.role.toSave = true;
                            }
                        } else {
                            if (msg.page > 0 && msg.page >= TraderPager.getPageCount(setup.role)) {
                                TraderPager.addPage(setup.role);
                            }
                            TraderPager.switchPage(setup.role, msg.page);
                        }
                        menu.detectAndSendChanges();
                    } else if (menu instanceof ContainerNPCTrader) {
                        TraderPager.switchPage(((ContainerNPCTrader) menu).role, msg.page);
                        menu.detectAndSendChanges();
                    }
                }
            });
            return null;
        }
    }
}
