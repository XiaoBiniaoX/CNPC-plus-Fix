package bin.cnpcplus.invpage.network;

import bin.cnpcplus.invpage.DropPageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import noppes.npcs.containers.ContainerNPCInv;
import noppes.npcs.entity.data.DataInventory;

public class PacketNpcInvPage implements IMessage {
    private int page;

    public PacketNpcInvPage() {}

    public PacketNpcInvPage(int page) {
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

    public static class Handler implements IMessageHandler<PacketNpcInvPage, IMessage> {
        @Override
        public IMessage onMessage(final PacketNpcInvPage msg, final MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Container menu = player.openContainer;
                    if (!(menu instanceof ContainerNPCInv)) return;
                    Slot slot4 = menu.getSlot(4);
                    if (slot4 == null || !(slot4.inventory instanceof DataInventory)) return;
                    DataInventory inv = (DataInventory) slot4.inventory;
                    DropPageStore.set(inv, msg.page);
                    menu.detectAndSendChanges();
                }
            });
            return null;
        }
    }
}
