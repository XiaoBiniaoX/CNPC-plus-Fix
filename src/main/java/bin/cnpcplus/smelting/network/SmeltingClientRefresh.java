package bin.cnpcplus.smelting.network;

import bin.cnpcplus.smelting.client.GuiSmeltingRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client only bridge for the sync packet.
 *
 * Kept separate so PacketSmeltingSync, which is loaded on both sides, never has a
 * client class in its imports. Referencing Minecraft directly from a common class
 * risks NoClassDefFoundError on a dedicated server.
 */
@SideOnly(Side.CLIENT)
public final class SmeltingClientRefresh {
    private SmeltingClientRefresh() {}

    public static void refresh(int selectedId) {
        GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen instanceof GuiSmeltingRecipes) {
            ((GuiSmeltingRecipes) screen).refreshFromServer(selectedId);
        }
    }
}
