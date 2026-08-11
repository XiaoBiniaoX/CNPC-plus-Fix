package bin.cnpcplus.trader.network;

import bin.cnpcplus.trader.TraderPager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.roles.RoleTrader;

/**
 * Server -> client page number relay. The client flips pages locally for
 * instant feedback; when the server container is (re)opened it announces
 * the server-side page so label/items never drift (1.21.1: B-page items
 * shown on A, ghost items).
 */
public class PacketTraderPageSync implements IMessage {
    private int page;

    public PacketTraderPageSync() {}

    public PacketTraderPageSync(int page) {
        this.page = page;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        page = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(page);
    }

    public static class Handler implements IMessageHandler<PacketTraderPageSync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketTraderPageSync msg, final MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                    if (!(screen instanceof GuiContainerNPCInterface)) return;
                    GuiContainerNPCInterface base = (GuiContainerNPCInterface) screen;
                    if (base.npc == null || !(base.npc.roleInterface instanceof RoleTrader)) return;
                    RoleTrader role = (RoleTrader) base.npc.roleInterface;
                    if (TraderPager.getPage(role) == msg.page) return;
                    TraderPager.switchPage(role, msg.page);
                    base.initGui();
                }
            });
            return null;
        }
    }
}