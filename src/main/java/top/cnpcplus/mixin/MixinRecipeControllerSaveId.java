package top.cnpcplus.mixin;

import net.minecraft.resources.ResourceLocation;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.client.PacketSyncRecipeUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.persist.PersistedRecipeStore;
import top.cnpcplus.persist.RecipeIds;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * 1) Never leave a recipe in both global and anvil maps.
 * 2) Legacy name-id "new" collision: only rekey brand-new empty recipes.
 */
@Mixin(value = RecipeController.class, remap = false)
public class MixinRecipeControllerSaveId {

    @Shadow public HashMap<ResourceLocation, RecipeCarpentry> globalRecipes;
    @Shadow public HashMap<ResourceLocation, RecipeCarpentry> anvilRecipes;

    @Inject(method = "saveRecipe", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$safeSave(RecipeCarpentry recipe, CallbackInfoReturnable<RecipeCarpentry> cir) {
        if (recipe == null || recipe.getId() == null) return;
        RecipeController self = (RecipeController) (Object) this;
        ResourceLocation id = recipe.getId();
        String path = id.getPath();

        // Legacy name-based ids only: if id taken by a *different valid* recipe and
        // incoming is empty/new, rekey instead of clobbering.
        boolean legacy = path == null || !path.startsWith("r_");
        if (legacy) {
            RecipeCarpentry existing = self.getRecipe(id);
            boolean persistHit = PersistedRecipeStore.contains(id);
            boolean incomingEmpty = !recipe.isValid();
            if (incomingEmpty && ((existing != null && existing.isValid()) || persistHit)) {
                recipe = cnpcplus$rekeyEmpty(recipe);
                id = recipe.getId();
            }
        }

        // Name de-dupe (same as vanilla, kept here because we cancel original).
        RecipeCarpentry current = self.getRecipe(id);
        if (current != null && current.name != null && recipe.name != null && !current.name.equals(recipe.name)) {
            while (cnpcplus$nameTaken(recipe.name)) {
                recipe.name = recipe.name + "_";
            }
        }

        // Critical: strip from BOTH maps so 3x3 never lingers in 4x4 list and vice versa.
        this.globalRecipes.remove(id);
        this.anvilRecipes.remove(id);
        if (recipe.isGlobal) {
            this.globalRecipes.put(id, recipe);
            Packets.sendAll(new PacketSyncRecipeUpdate(id, 6, recipe.writeNBT()));
        } else {
            this.anvilRecipes.put(id, recipe);
            Packets.sendAll(new PacketSyncRecipeUpdate(id, 7, recipe.writeNBT()));
        }
        cnpcplus$saveCategories(self);
        self.reloadGlobalRecipes();
        cir.setReturnValue(recipe);
    }

    @Unique
    private boolean cnpcplus$nameTaken(String name) {
        String lower = name.toLowerCase();
        for (RecipeCarpentry r : this.globalRecipes.values()) {
            if (r.name != null && r.name.toLowerCase().equals(lower)) return true;
        }
        for (RecipeCarpentry r : this.anvilRecipes.values()) {
            if (r.name != null && r.name.toLowerCase().equals(lower)) return true;
        }
        return false;
    }

    @Unique
    private static RecipeCarpentry cnpcplus$rekeyEmpty(RecipeCarpentry recipe) {
        ResourceLocation fresh = RecipeIds.fresh();
        String name = RecipeIds.uniqueDisplayName(recipe.name == null ? "new" : recipe.name);
        RecipeCarpentry rekeyed = new RecipeCarpentry(fresh, name);
        rekeyed.copy(recipe);
        rekeyed.name = name;
        rekeyed.isGlobal = recipe.isGlobal;
        rekeyed.ignoreDamage = recipe.ignoreDamage;
        rekeyed.ignoreNBT = recipe.ignoreNBT;
        rekeyed.availability = recipe.availability;
        return rekeyed;
    }

    @Unique
    private static void cnpcplus$saveCategories(RecipeController self) {
        try {
            Method m = RecipeController.class.getDeclaredMethod("saveCategories");
            m.setAccessible(true);
            m.invoke(self);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
