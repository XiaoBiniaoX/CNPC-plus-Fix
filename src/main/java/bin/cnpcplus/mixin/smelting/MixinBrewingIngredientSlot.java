package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.station.StationRecipes;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.inventory.ContainerBrewingStand$Ingredient")
public abstract class MixinBrewingIngredientSlot extends Slot {
    protected MixinBrewingIngredientSlot(IInventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
    @Inject(method = "isItemValid", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        boolean client = inventory instanceof net.minecraft.tileentity.TileEntity && ((net.minecraft.tileentity.TileEntity)inventory).getWorld().isRemote;
        if (StationRecipes.accepts(1, 3, stack, client)) cir.setReturnValue(true);
    }
}
