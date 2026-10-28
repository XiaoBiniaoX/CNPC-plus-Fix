package bin.cnpcplus.smelting;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Opens the recipe editor.
 *
 * 1.12.2 has no MenuType, so this goes through Forge's IGuiHandler. The selected
 * recipe id rides along in the x parameter, which is the usual trick for this
 * version.
 */
public final class SmeltingGuiOpener implements IGuiHandler {
    public static final int GUI_ID = 71;

    private SmeltingGuiOpener() {}

    public static void register() {
        NetworkRegistry.INSTANCE.registerGuiHandler(getMod(), new SmeltingGuiOpener());
    }

    private static Object getMod() {
        return net.minecraftforge.fml.common.Loader.instance()
                .getIndexedModList().get(CnpcPlus.MODID).getMod();
    }

    /** Server side entry point; permission is checked by the caller. */
    public static void open(EntityPlayerMP player, int selectedId) {
        if (player == null) {
            return;
        }
        player.openGui(getMod(), GUI_ID, player.world, selectedId, 0, 0);
        bin.cnpcplus.smelting.network.SmeltingSync.sendTo(player, selectedId);
    }

    public static void openRecipe(EntityPlayerMP player, bin.cnpcplus.crafting.CraftKind kind) {
        player.openGui(getMod(), kind.guiId(), player.world, 0, 0, 0);
        if (player.openContainer instanceof bin.cnpcplus.crafting.RecipeContainer) {
            bin.cnpcplus.crafting.network.PacketRecipeState.send(player,
                    (bin.cnpcplus.crafting.RecipeContainer) player.openContainer);
        }
    }

    public static void openSmithing(EntityPlayerMP player, net.minecraft.util.math.BlockPos pos) {
        player.openGui(getMod(), 75, player.world, pos.getX(), pos.getY(), pos.getZ());
    }

    public static void openVillager(EntityPlayerMP player, noppes.npcs.entity.EntityNPCInterface npc, boolean editing) {
        player.openGui(getMod(), editing ? 77 : 76, player.world, npc.getEntityId(), 0, 0);
        if (player.openContainer instanceof bin.cnpcplus.villagertrade.VillagerContainer) {
            ((bin.cnpcplus.villagertrade.VillagerContainer) player.openContainer).sync();
        }
    }

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if ((id == 76 || id == 77) && player instanceof EntityPlayerMP) {
            net.minecraft.entity.Entity entity = world.getEntityByID(x);
            if (!(entity instanceof noppes.npcs.entity.EntityNPCInterface)) return null;
            noppes.npcs.entity.EntityNPCInterface npc = (noppes.npcs.entity.EntityNPCInterface) entity;
            if (!(npc.roleInterface instanceof bin.cnpcplus.villagertrade.RoleVillagerTrader) || npc.isKilled()) return null;
            if (id == 77 && (noppes.npcs.NoppesUtilServer.getEditingNpc((EntityPlayerMP) player) != npc
                    || !noppes.npcs.CustomNpcsPermissions.hasPermission(player, noppes.npcs.CustomNpcsPermissions.NPC_ADVANCED))) return null;
            if (id == 76 && npc.getDistanceSq(player) > 64) return null;
            return new bin.cnpcplus.villagertrade.VillagerContainer(player, npc, id == 77);
        }
        if (id == 75 && player != null) return new bin.cnpcplus.crafting.SmithingContainer(player, new net.minecraft.util.math.BlockPos(x, y, z));
        bin.cnpcplus.crafting.CraftKind kind = bin.cnpcplus.crafting.CraftKind.fromGui(id);
        if (kind != null && SmeltingPermissions.canEdit(player)) return new bin.cnpcplus.crafting.RecipeContainer(player, kind);
        if (id != GUI_ID || player == null) {
            return null;
        }
        // Re-checked here as well: this is the point where the container is created.
        if (!SmeltingPermissions.canEdit(player)) {
            return null;
        }
        return new ContainerSmeltingRecipes(player.inventory, x);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if ((id == 76 || id == 77) && player != null) {
            net.minecraft.entity.Entity entity = world.getEntityByID(x);
            return new bin.cnpcplus.villagertrade.client.VillagerScreen(new bin.cnpcplus.villagertrade.VillagerContainer(player,
                    entity instanceof noppes.npcs.entity.EntityNPCInterface ? (noppes.npcs.entity.EntityNPCInterface) entity : null, id == 77));
        }
        if (id == 75 && player != null) return new bin.cnpcplus.crafting.client.SmithingScreen(new bin.cnpcplus.crafting.SmithingContainer(player, new net.minecraft.util.math.BlockPos(x, y, z)));
        bin.cnpcplus.crafting.CraftKind kind = bin.cnpcplus.crafting.CraftKind.fromGui(id);
        if (kind != null && player != null) return new bin.cnpcplus.crafting.client.RecipeEditor(new bin.cnpcplus.crafting.RecipeContainer(player, kind));
        if (id != GUI_ID || player == null) {
            return null;
        }
        return new bin.cnpcplus.smelting.client.GuiSmeltingRecipes(
                new ContainerSmeltingRecipes(player.inventory, x), x);
    }
}
