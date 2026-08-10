package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.RecipeControllerFacade;
import net.minecraft.inventory.InventoryCrafting;
import noppes.npcs.EventHooks;
import noppes.npcs.api.handler.IRecipeHandler;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeController.class)
public class MixinRecipeController {

    @Inject(method = "load", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusLoad(CallbackInfo ci) {
        RecipeController self = (RecipeController) (Object) this;
        RecipeControllerFacade.loadAll(self);
        EventHooks.onGlobalRecipesLoaded((IRecipeHandler) self);
        ci.cancel();
    }

    @Inject(method = "reloadGlobalRecipes", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusReloadGlobal(CallbackInfo ci) {
        RecipeControllerFacade.reloadGlobalRecipes((RecipeController) (Object) this);
        ci.cancel();
    }

    @Inject(method = "saveRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusSave(RecipeCarpentry recipe, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.saveRecipe(recipe, (RecipeController) (Object) this));
    }

    @Inject(method = "delete", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusDelete(int id, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.delete(id, (RecipeController) (Object) this));
    }

    @Inject(method = "getRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusGet(int id, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.getRecipe(id));
    }

    @Inject(method = "findMatchingRecipe", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusFind(InventoryCrafting inventoryCrafting, CallbackInfoReturnable<RecipeCarpentry> cir) {
        cir.setReturnValue(RecipeControllerFacade.findMatchingRecipe(inventoryCrafting));
    }
}
