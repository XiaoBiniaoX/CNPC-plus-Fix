package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.runtime.CraftingInputMatcher;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Phase3-D: official matches/checkMatch is broken (hardcoded 4x4 and empty stack compare).
 * Restore correct CraftingInput matching for workbench; pure function, no storage side effects.
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryMatches {

    @Shadow(remap = false)
    public boolean isGlobal;

    @Inject(method = "matches", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusMatches(CraftingInput inventoryCrafting, Level world, CallbackInfoReturnable<Boolean> cir) {
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        // Always use fixed matcher: official checkMatch never reads inventory items (always EMPTY)
        cir.setReturnValue(CraftingInputMatcher.matches(self, inventoryCrafting));
    }

    @Inject(method = "canCraftInDimensions", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusCanCraft(int width, int height, CallbackInfoReturnable<Boolean> cir) {
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        int rw = Math.max(1, self.getWidth());
        int rh = Math.max(1, self.getHeight());
        cir.setReturnValue(width >= rw && height >= rh);
    }
}