package bin.cnpcplus.station;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.smelting.SmeltingGuiOpener;
import bin.cnpcplus.smelting.SmeltingPermissions;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.*;
import java.io.IOException;

public final class StationPacket implements IMessage {
    public int action, kind, window;
    public NBTTagCompound data;
    public StationPacket() {}
    public StationPacket(int action, int kind, int window, NBTTagCompound data) { this.action = action; this.kind = kind; this.window = window; this.data = data; }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(action); buf.writeInt(kind); buf.writeInt(window); ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) {
        action = buf.readInt(); kind = buf.readInt(); window = buf.readInt(); data = ByteBufUtils.readTag(buf);
        if (kind < 0 || kind > 2 || data == null) throw new IllegalArgumentException("Invalid station packet");
    }
    public static void sync(EntityPlayerMP player, StationContainer container, String notice, int result) {
        NBTTagCompound tag = container.recipe.save(false); NBTTagList entries = new NBTTagList();
        for (StationRecipe r : StationRecipes.catalogue(container.kind).values()) {
            NBTTagCompound entry = new NBTTagCompound(); entry.setInteger("Id", r.id); entry.setString("Name", r.name); entries.appendTag(entry);
        }
        tag.setTag("Recipes", entries); tag.setString("Notice", notice); tag.setInteger("Result", result);
        CraftingViewNetwork.CHANNEL.sendTo(new StationPacket(5, container.kind, container.windowId, tag), player);
    }
    public static final class Handler implements IMessageHandler<StationPacket, IMessage> {
        @Override public IMessage onMessage(StationPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (!SmeltingPermissions.canEdit(player)) return;
                if (msg.action == 0) { SmeltingGuiOpener.openStation(player, msg.kind); return; }
                if (!(player.openContainer instanceof StationContainer) || player.openContainer.windowId != msg.window) return;
                StationContainer container = (StationContainer)player.openContainer;
                if (container.kind != msg.kind) return;
                int id = msg.data.getInteger("Id"), result = 0; String notice = "";
                java.util.NavigableMap<Integer, StationRecipe> catalogue = StationRecipes.catalogue(msg.kind);
                try {
                    if (msg.action == 1) container.select(catalogue.get(id));
                    else if (msg.action == 2) container.select(null);
                    else if (msg.action == 3) {
                        if (id != container.recipe.id || id >= 0 && !catalogue.containsKey(id)) return;
                        StationRecipe recipe = StationRecipe.read(msg.data); container.readSlots(recipe);
                        if (!recipe.valid(msg.kind)) { sync(player, container, "cnpcplus.recipe.error.invalid", -1); return; }
                        if (recipe.id < 0) recipe.id = catalogue.isEmpty() ? 1 : catalogue.lastKey() + 1;
                        StationRecipe previous = catalogue.put(recipe.id, recipe);
                        try { StationRecipes.persist(msg.kind); }
                        catch (IOException error) { if (previous == null) catalogue.remove(recipe.id); else catalogue.put(previous.id, previous); throw error; }
                        container.select(recipe); result = 1; notice = "cnpcplus.station.saved";
                        StationCataloguePacket.sync(msg.kind, null);
                    } else if (msg.action == 4) {
                        if (id < 0 || id != container.recipe.id) return;
                        StationRecipe old = catalogue.remove(id);
                        try { StationRecipes.persist(msg.kind); }
                        catch (IOException error) { if (old != null) catalogue.put(id, old); throw error; }
                        container.select(null); result = 2; notice = "cnpcplus.station.removed";
                        StationCataloguePacket.sync(msg.kind, null);
                    } else return;
                } catch (IOException | IllegalArgumentException error) {
                    CnpcPlus.LOGGER.error("Station recipe edit failed for {}", player.getName(), error); result = -1; notice = "cnpcplus.recipe.error.io";
                }
                sync(player, container, notice, result);
            });
            return null;
        }
    }
    public static final class ClientHandler implements IMessageHandler<StationPacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(StationPacket msg, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (net.minecraft.client.Minecraft.getMinecraft().currentScreen instanceof StationScreen)
                    ((StationScreen)net.minecraft.client.Minecraft.getMinecraft().currentScreen).accept(msg);
            }); return null;
        }
    }
}
