package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-recipe cook time for the vanilla furnace.
 *
 * getCookTime is an instance method in 1.12.2 (getCookTime(ItemStack)I ->
 * func_174904_a), so the input slot is reachable and the recipe can be
 * identified from the furnace itself.
 *
 * Fuel is deliberately not handled here. isItemFuel is static, so it has no
 * furnace to inspect and cannot tell which recipe is running; it is also just
 * getItemBurnTime(stack) > 0, so hooking the burn time covers it for free.
 * Burn time lives in SmeltingBurnTimeHandler.
 */
@Mixin(TileEntityFurnace.class)
public class MixinTileEntityFurnaceSmelting {
    @Inject(method = "getCookTime", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$customCookTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        SmeltingRecipeData data = SmeltingFuelRules.findByInput(stack);
        if (data != null) {
            cir.setReturnValue(Integer.valueOf(Math.max(1, Math.round(data.cookTime))));
        }
    }
}
