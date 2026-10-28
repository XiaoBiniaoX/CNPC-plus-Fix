package bin.cnpcplus.crafting.network;

import bin.cnpcplus.crafting.CustomRecipe;
import bin.cnpcplus.crafting.RecipeContainer;
import bin.cnpcplus.crafting.RecipeStore;
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

/** 只同步目录摘要和当前配方参数；大物品 NBT 留给容器槽同步。 */
public final class PacketRecipeState implements IMessage {
    public int window, kind;
    public NBTTagCompound data = new NBTTagCompound();
    /** 非空时客户端弹一条提示（保存失败原因）。 */
    public String notice = "";
    public PacketRecipeState() { }
    public static void send(EntityPlayerMP player, RecipeContainer container) {
        PacketRecipeState message = new PacketRecipeState();
        message.window = container.windowId;
        message.kind = container.kind.ordinal();
        message.data = container.recipe.toNBT(false);
        message.notice = RecipeStore.takeNotice();
        NBTTagList entries = new NBTTagList();
        for (CustomRecipe recipe : RecipeStore.list(container.kind)) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Id", recipe.id);
            entry.setString("Name", recipe.name);
            entries.appendTag(entry);
        }
        message.data.setTag("Entries", entries);
        CraftingViewNetwork.CHANNEL.sendTo(message, player);
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(window); buf.writeInt(kind); ByteBufUtils.writeTag(buf, data); ByteBufUtils.writeUTF8String(buf, notice == null ? "" : notice); }
    @Override public void fromBytes(ByteBuf buf) { window = buf.readInt(); kind = buf.readInt(); data = ByteBufUtils.readTag(buf); notice = ByteBufUtils.readUTF8String(buf); }

    public static final class Handler implements IMessageHandler<PacketRecipeState, IMessage> {
        @Override @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketRecipeState message, MessageContext context) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (message.data == null) return;
                bin.cnpcplus.crafting.client.RecipeClientState.accept(message);
            });
            return null;
        }
    }
}
