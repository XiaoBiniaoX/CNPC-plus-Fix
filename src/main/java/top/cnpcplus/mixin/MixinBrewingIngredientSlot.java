package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.station.StationRecipes;

@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$IngredientsSlot")
public class MixinBrewingIngredientSlot {
    @Inject(method = "mayPlace", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (StationRecipes.accepts(1, 3, stack, false)) cir.setReturnValue(true);
    }
}
