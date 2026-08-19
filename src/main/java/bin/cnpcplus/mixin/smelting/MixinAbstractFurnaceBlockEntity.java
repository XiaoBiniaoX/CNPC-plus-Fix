package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class MixinAbstractFurnaceBlockEntity implements SmeltingFuelRules.FurnaceTypeAccess {
    @Shadow(remap = false) private RecipeType<?> recipeType;

    @Override public RecipeType<?> cnpcplus$getRecipeType() { return recipeType; }

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$allowAnyFuelSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == 1 && stack != null && !stack.isEmpty()) cir.setReturnValue(true);
    }

    @Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$customFuelBurnTime(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Integer custom = SmeltingFuelRules.customBurnTime((AbstractFurnaceBlockEntity) (Object) this, stack);
        if (custom != null) cir.setReturnValue(custom);
    }
}
