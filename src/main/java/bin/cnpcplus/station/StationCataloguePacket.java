package bin.cnpcplus.station;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.*;
import java.util.ArrayList;
import java.util.List;

/** Clients never read a server world's recipe files, including in an integrated server. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class StationCataloguePacket implements IMessage {
    private int kind;
    private NBTTagCompound data;
    public StationCataloguePacket() {}
    private StationCataloguePacket(int kind, StationRecipe recipe) {
        this.kind = kind; data = recipe == null ? new NBTTagCompound() : recipe.save(true);
    }
    public static void sync(int kind, EntityPlayerMP player) {
        // Keep each recipe separate: Forge's NBT decoder limits a single tag to 2 MiB.
        List<StationCataloguePacket> packets = new ArrayList<>(); packets.add(new StationCataloguePacket(kind, null));
        for (StationRecipe recipe : StationRecipes.catalogue(kind).values()) packets.add(new StationCataloguePacket(kind, recipe));
        for (StationCataloguePacket packet : packets) {
            if (player == null) CraftingViewNetwork.CHANNEL.sendToAll(packet);
            else CraftingViewNetwork.CHANNEL.sendTo(packet, player);
        }
    }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(kind); ByteBufUtils.writeTag(buf, data); }
    @Override public void fromBytes(ByteBuf buf) {
        kind = buf.readInt(); data = ByteBufUtils.readTag(buf);
        if (kind < 0 || kind > 2 || data == null) throw new IllegalArgumentException("Invalid station catalogue");
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) for (int kind = 0; kind < 3; kind++)
            sync(kind, (EntityPlayerMP)event.player);
    }
    public static final class Handler implements IMessageHandler<StationCataloguePacket, IMessage> {
        @Override @SideOnly(Side.CLIENT) public IMessage onMessage(StationCataloguePacket msg, MessageContext ctx) {
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                if (msg.data.isEmpty()) StationRecipes.CLIENT.put(msg.kind, new ArrayList<>());
                else StationRecipes.CLIENT.computeIfAbsent(msg.kind, key -> new ArrayList<>()).add(StationRecipe.read(msg.data));
            }); return null;
        }
    }
}
