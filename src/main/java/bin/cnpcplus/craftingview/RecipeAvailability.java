package bin.cnpcplus.craftingview;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.constants.*;
import noppes.npcs.controllers.data.Availability;

/** Native CNPC conditions; automation never substitutes a fake player. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class RecipeAvailability {
    private RecipeAvailability() {}
    public static Availability read(NBTTagCompound tag) {
        Availability result = new Availability();
        if (tag.isEmpty()) return result;
        for (String key : new String[]{"AvailabilityDialog", "AvailabilityDialog2", "AvailabilityDialog3", "AvailabilityDialog4",
                "AvailabilityQuest", "AvailabilityQuest2", "AvailabilityQuest3", "AvailabilityQuest4",
                "AvailabilityFaction", "AvailabilityFaction2", "AvailabilityFactionStance", "AvailabilityFaction2Stance",
                "AvailabilityScoreboardType", "AvailabilityScoreboard2Type", "AvailabilityDayTime"}) {
            int size = key.startsWith("AvailabilityDialog") ? EnumAvailabilityDialog.values().length
                    : key.startsWith("AvailabilityQuest") ? EnumAvailabilityQuest.values().length
                    : key.contains("Stance") ? EnumAvailabilityFaction.values().length
                    : key.startsWith("AvailabilityFaction") ? EnumAvailabilityFactionType.values().length
                    : key.contains("Scoreboard") ? EnumAvailabilityScoreboard.values().length : EnumDayTime.values().length;
            int value = tag.getInteger(key);
            if (value < 0 || value >= size) throw new IllegalArgumentException("Invalid recipe availability: " + key);
        }
        // Native readFromNBT initializes the server scoreboard. Packet decoders/client screens must not do that.
        String score = tag.getString("AvailabilityScoreboardObjective"), score2 = tag.getString("AvailabilityScoreboard2Objective");
        NBTTagCompound input = tag;
        if (noppes.npcs.CustomNpcs.Server == null || !noppes.npcs.CustomNpcs.Server.isCallingFromMinecraftThread()) {
            input = tag.copy(); input.removeTag("AvailabilityScoreboardObjective"); input.removeTag("AvailabilityScoreboard2Objective");
        }
        result.readFromNBT(input);
        result.scoreboardObjective = score; result.scoreboard2Objective = score2;
        return result;
    }
    public static boolean hasOptions(Availability a) {
        return a.dialogAvailable != EnumAvailabilityDialog.Always || a.dialog2Available != EnumAvailabilityDialog.Always
                || a.dialog3Available != EnumAvailabilityDialog.Always || a.dialog4Available != EnumAvailabilityDialog.Always
                || a.questAvailable != EnumAvailabilityQuest.Always || a.quest2Available != EnumAvailabilityQuest.Always
                || a.quest3Available != EnumAvailabilityQuest.Always || a.quest4Available != EnumAvailabilityQuest.Always
                || a.factionAvailable != EnumAvailabilityFactionType.Always || a.faction2Available != EnumAvailabilityFactionType.Always
                || a.daytime != EnumDayTime.Always || !a.scoreboardObjective.isEmpty() || !a.scoreboard2Objective.isEmpty() || a.minPlayerLevel > 0;
    }
    public static boolean allows(Availability a, EntityPlayer player) { return !hasOptions(a) || player != null && a.isAvailable(player); }
    public static void refreshCrafting(net.minecraft.inventory.Container container) {
        if (container instanceof net.minecraft.inventory.ContainerWorkbench) {
            net.minecraft.inventory.ContainerWorkbench bench = (net.minecraft.inventory.ContainerWorkbench)container;
            bench.onCraftMatrixChanged(bench.craftMatrix);
        } else if (container instanceof noppes.npcs.containers.ContainerCarpentryBench) {
            noppes.npcs.containers.ContainerCarpentryBench bench = (noppes.npcs.containers.ContainerCarpentryBench)container;
            bench.func_75130_a(bench.craftMatrix);
        }
    }
    @SubscribeEvent public static void playerTick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END || event.player.world.isRemote || event.player.ticksExisted % 20 != 0) return;
        refreshCrafting(event.player.openContainer);
        if (event.player.openContainer instanceof net.minecraft.inventory.ContainerRepair)
            ((net.minecraft.inventory.ContainerRepair)event.player.openContainer).updateRepairOutput();
        else if (event.player.openContainer instanceof bin.cnpcplus.crafting.SmithingContainer)
            event.player.openContainer.onCraftMatrixChanged(null);
        else if (event.player.openContainer instanceof bin.cnpcplus.station.GrindstoneContainer)
            event.player.openContainer.onCraftMatrixChanged(null);
    }
    public static EntityPlayer operator(TileEntity tile) {
        NBTTagCompound tag = tile.getTileData();
        if (!(tile.getWorld() instanceof WorldServer) || !tag.hasUniqueId("CNPCPlusRecipeOperator")) return null;
        EntityPlayer player = tile.getWorld().getMinecraftServer().getPlayerList().getPlayerByUUID(tag.getUniqueId("CNPCPlusRecipeOperator"));
        return player != null && player.world == tile.getWorld() ? player : null;
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote) return;
        TileEntity tile = event.getWorld().getTileEntity(event.getPos());
        if (tile instanceof net.minecraft.tileentity.TileEntityFurnace || tile instanceof net.minecraft.tileentity.TileEntityBrewingStand) {
            tile.getTileData().setUniqueId("CNPCPlusRecipeOperator", event.getEntityPlayer().getUniqueID()); tile.markDirty();
        }
    }
}
