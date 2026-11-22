package top.cnpcplus.mixin;

import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.Recipe;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.packets.client.PacketSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PacketSync.class, remap = false)
public abstract class MixinPacketSyncRecipeList {
    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnoppes/npcs/controllers/RecipeController;reloadGlobalRecipes()V"))
    private void cnpcplus$loadClientRecipeList(RecipeController controller) {
        var manager = Minecraft.getInstance().getConnection().getRecipeManager();
        var recipes = new ArrayList<Recipe<?>>();
        for (var recipe : manager.getRecipes()) {
            if (!controller.globalRecipes.containsKey(recipe.getId())) recipes.add(recipe);
        }
        for (var recipe : controller.globalRecipes.values()) {
            if (recipe.isValid()) recipes.add(recipe);
        }
        manager.replaceRecipes(recipes);
    }
}
