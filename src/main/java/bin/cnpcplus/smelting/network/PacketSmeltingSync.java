package bin.cnpcplus.smelting.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server to client recipe list.
 *
 * Clients never read smelting_recipes.dat; this packet is the only way they learn
 * about recipes, so what they show always matches the server. In 1.12.2 the
 * client also needs the real data (there is no vanilla recipe sync in this
 * version) for furnace progress and fuel acceptance to render correctly.
 *
 * This class is loaded on both sides, so it must not reference client-only types
 * outside a @SideOnly method: an import touched during server class loading would
 * be a NoClassDefFoundError on a dedicated server.
 */
public class PacketSmeltingSync implements IMessage {
    private static final int MAX_RECIPES = 256;

    private NBTTagCompound payload = new NBTTagCompound();
    private int selectedId = -1;

    public PacketSmeltingSync() {}

    public PacketSmeltingSync(List<SmeltingRecipeData> recipes, int selectedId) {
        NBTTagList list = new NBTTagList();
        if (recipes != null) {
            for (int i = 0; i < recipes.size() && i < MAX_RECIPES; ++i) {
                SmeltingRecipeData data = recipes.get(i);
                if (data != null) {
                    list.appendTag(data.toNBT());
                }
            }
        }
        this.payload = new NBTTagCompound();
        this.payload.setTag("Data", list);
        this.selectedId = selectedId;
    }

    public int getSelectedId() {
        return this.selectedId;
    }

    public List<SmeltingRecipeData> getRecipes() {
        List<SmeltingRecipeData> out = new ArrayList<SmeltingRecipeData>();
        NBTTagList list = this.payload.getTagList("Data", 10);
        for (int i = 0; i < list.tagCount() && i < MAX_RECIPES; ++i) {
            SmeltingRecipeData data = SmeltingRecipeData.fromNBT(list.getCompoundTagAt(i));
            if (data != null && data.id >= 0) {
                out.add(data);
            }
        }
        return out;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.selectedId = buf.readInt();
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        this.payload = tag == null ? new NBTTagCompound() : tag;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.selectedId);
        ByteBufUtils.writeTag(buf, this.payload);
    }

    public static class Handler implements IMessageHandler<PacketSmeltingSync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketSmeltingSync msg, final MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        SmeltingRecipeRegistry.acceptSync(msg.getRecipes());
                        SmeltingClientRefresh.refresh(msg.getSelectedId());
                    } catch (Throwable t) {
                        CnpcPlus.LOGGER.error("[Smelting] client sync failed", t);
                    }
                }
            });
            return null;
        }
    }
}
