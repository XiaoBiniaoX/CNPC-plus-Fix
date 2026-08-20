package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import net.minecraft.inventory.SlotFurnaceFuel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a recipe's specified fuel be placed in the fuel slot.
 *
 * A specified fuel is usually an item vanilla does not consider fuel at all
 * (armour, rails, a diamond). SlotFurnaceFuel.isItemValid would refuse to accept
 * it, so the player could never load it and the recipe would be unusable.
 *
 * This only ever widens what the slot accepts; it never rejects something
 * vanilla allows, so ordinary furnaces behave exactly as before.
 *
 * The slot has no reference to its furnace, so the recipe cannot be identified
 * here. Any recipe naming this item as its fuel is enough to allow the placement;
 * whether it actually burns is decided by the furnace hooks, which do know the
 * input item.
 */
@Mixin(SlotFurnaceFuel.class)
public class MixinSlotFurnaceFuelSmelting {
    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$allowSpecifiedFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (SmeltingRecipeData data : bin.cnpcplus.smelting.SmeltingRecipeRegistry.list()) {
            if (data != null && !data.fuel.isEmpty()
                    && SmeltingFuelRules.stackMatches(stack, data.fuel)) {
                cir.setReturnValue(Boolean.TRUE);
                return;
            }
        }
    }
}
