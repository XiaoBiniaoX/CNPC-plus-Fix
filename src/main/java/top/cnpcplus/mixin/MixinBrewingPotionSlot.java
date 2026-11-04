package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.station.StationRecipes;

@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public class MixinBrewingPotionSlot {
    @Inject(method = "mayPlaceItem", at = @At("RETURN"), cancellable = true, require = 1)
    private static void cnpcplus$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        for (int i = 0; i < 3; i++) if (StationRecipes.accepts(1, i, stack, false)) { cir.setReturnValue(true); return; }
    }
    @Inject(method = "getMaxStackSize", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$outputCount(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = ((Slot)(Object)this).getItem();
        if (stack.getCount() > 1) cir.setReturnValue(stack.getMaxStackSize());
    }
}
