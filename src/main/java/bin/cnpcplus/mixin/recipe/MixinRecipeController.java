package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.RecipeControllerFacade;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.CraftingContainer;
import noppes.npcs.EventHooks;
import noppes.npcs.api.handler.IRecipeHandler;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Restores behavior intentionally stubbed by upstream 1.21.1 NeoForge RecipeController.
 */
@Mixin(RecipeController.class)
public class MixinRecipeController {

    @Inject(method = "load(Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusLoad(HolderLookup.Provider provider, CallbackInfo ci) {
        RecipeController self = (RecipeController) (Object) this;
        RecipeControllerFacade.loadAll(provider, self);
        self.reloadGlobalRecipes();
        EventHooks.onGlobalRecipesLoaded((IRecipeHandler) self);
        ci.cancel();
    }

    @Inject(method = "saveRecipe(Lnoppes/npcs/controllers/data/RecipeCarpentry;)Lnoppes/npcs/controllers/data/RecipeCarpentry;", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusSave(RecipeCarpentry recipe, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.saveRecipe(recipe, (RecipeController) (Object) this));
    }

    @Inject(method = "delete(I)Lnoppes/npcs/controllers/data/RecipeCarpentry;", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusDelete(int id, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.delete(id, (RecipeController) (Object) this));
    }

    @Inject(method = "getRecipe(I)Lnoppes/npcs/controllers/data/RecipeCarpentry;", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusGet(int id, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.getRecipe(id));
    }

    @Inject(method = "findMatchingRecipe(Lnet/minecraft/world/inventory/CraftingContainer;)Lnoppes/npcs/controllers/data/RecipeCarpentry;", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusFind(CraftingContainer inventoryCrafting, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.findMatchingRecipe(inventoryCrafting));
    }
}