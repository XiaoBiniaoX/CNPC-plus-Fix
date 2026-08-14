package top.cnpcplus.mixin;

import net.minecraft.resources.ResourceLocation;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.persist.PersistedRecipeStore;

import java.lang.reflect.Method;
import java.util.HashMap;

@Mixin(value = RecipeController.class, remap = false)
public class MixinRecipeControllerPersist {

    @Shadow public HashMap<ResourceLocation, RecipeCarpentry> globalRecipes;
    @Shadow public HashMap<ResourceLocation, RecipeCarpentry> anvilRecipes;

    @Inject(method = "load", at = @At("RETURN"))
    private void cnpcplus$mergePersisted(CallbackInfo ci) {
        RecipeController self = (RecipeController) (Object) this;
        boolean dirty = false;
        for (RecipeCarpentry recipe : PersistedRecipeStore.list()) {
            ResourceLocation id = recipe.getId();
            if (id == null || self.getRecipe(id) != null) continue;
            if (recipe.isGlobal) {
                this.globalRecipes.put(id, recipe);
            } else {
                this.anvilRecipes.put(id, recipe);
            }
            dirty = true;
        }
        if (!dirty) return;
        // Copy into this world's recipes.dat so later un-persist won't strip them here.
        cnpcplus$saveCategories(self);
        self.reloadGlobalRecipes();
    }

    @Unique
    private static void cnpcplus$saveCategories(RecipeController self) {
        try {
            Method m = RecipeController.class.getDeclaredMethod("saveCategories");
            m.setAccessible(true);
            m.invoke(self);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("保存配方分类失败", e);
        }
    }
}
