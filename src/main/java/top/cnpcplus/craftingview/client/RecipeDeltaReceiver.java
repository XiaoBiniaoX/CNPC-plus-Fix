package top.cnpcplus.craftingview.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.craftingview.RecipeDeltaPacket;
import top.cnpcplus.craftingview.RecipeIndexUpdates;

public final class RecipeDeltaReceiver {
    private RecipeDeltaReceiver() {}
    public static void accept(RecipeDeltaPacket packet) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) RecipeIndexUpdates.replace(connection.getRecipeManager(), packet.id(), packet.recipe());
        top.cnpcplus.client.gui.SubGuiQuestSettings.refreshCraftRecipes();
    }
}
