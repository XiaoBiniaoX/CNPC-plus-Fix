package bin.cnpcplus.villagertrade;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class PacketVillageState implements IMessage {
    public int window, selected;
    public NBTTagCompound data = new NBTTagCompound();
    public PacketVillageState() { }
    public static void send(VillagerContainer container, EntityPlayerMP player) {
        PacketVillageState message = new PacketVillageState(); message.window = container.windowId; message.selected = container.selected;
        NBTTagList list = new NBTTagList(); for (TradeData trade : container.trades) list.appendTag(trade.toNBT());
        message.data.setTag("Trades", list);
        CraftingViewNetwork.CHANNEL.sendTo(message, player);
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(window); buf.writeInt(selected); ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readInt(); selected = buf.readInt(); data = ByteBufUtils.readTag(buf); }
    public static final class Handler implements IMessageHandler<PacketVillageState, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(PacketVillageState message, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> bin.cnpcplus.villagertrade.client.VillagerScreen.receive(message));
            return null;
        }
    }
}
