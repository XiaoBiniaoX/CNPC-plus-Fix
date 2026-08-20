package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes custom smelting recipes visible to the vanilla recipe lookup.
 *
 * Hooking FurnaceRecipes rather than editing its smeltingList map keeps the
 * recipes out of the map entirely: nothing to clean up when a world unloads, no
 * risk of leaking one save's recipes into another, and the vanilla map stays
 * untouched for other mods that iterate it.
 *
 * Custom recipes take priority over vanilla ones for the same input, which is
 * what the editor implies: you defined a recipe for this item, so it wins.
 *
 * Both methods are remapped MC methods, so remap defaults to true here.
 */
@Mixin(FurnaceRecipes.class)
public class MixinFurnaceRecipesSmelting {
    @Inject(method = "getSmeltingResult", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$customResult(ItemStack input, CallbackInfoReturnable<ItemStack> cir) {
        SmeltingRecipeData data = SmeltingFuelRules.findByInput(input);
        if (data != null) {
            cir.setReturnValue(data.output.copy());
        }
    }

    @Inject(method = "getSmeltingExperience", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$customExperience(ItemStack output, CallbackInfoReturnable<Float> cir) {
        // Matched on the produced stack, which is what vanilla passes here.
        if (output == null || output.isEmpty()) {
            return;
        }
        for (SmeltingRecipeData data : bin.cnpcplus.smelting.SmeltingRecipeRegistry.list()) {
            if (data != null && SmeltingFuelRules.stackMatches(output, data.output)) {
                cir.setReturnValue(Float.valueOf(data.xp));
                return;
            }
        }
    }
}
