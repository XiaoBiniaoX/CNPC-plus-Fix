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

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
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
        if (id != GUI_ID || player == null) {
            return null;
        }
        return new bin.cnpcplus.smelting.client.GuiSmeltingRecipes(
                new ContainerSmeltingRecipes(player.inventory, x), x);
    }
}
