package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.runtime.CraftingInputMatcher;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Official matches hardcodes 4x4 and has broken empty-stack logic.
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryMatches {

    @Inject(method = "func_77569_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusMatches(InventoryCrafting inv, World world, CallbackInfoReturnable<Boolean> cir) {
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        cir.setReturnValue(Boolean.valueOf(CraftingInputMatcher.matches(self, inv)));
    }
}
