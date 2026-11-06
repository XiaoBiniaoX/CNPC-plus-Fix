package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.station.StationRecipes;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.inventory.ContainerBrewingStand$Potion")
public abstract class MixinBrewingPotionSlot extends Slot {
    protected MixinBrewingPotionSlot(IInventory inventory, int index, int x, int y) { super(inventory, index, x, y); }
    @Inject(method = "isItemValid", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$accept(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        boolean client = inventory instanceof net.minecraft.tileentity.TileEntity && ((net.minecraft.tileentity.TileEntity)inventory).getWorld().isRemote;
        if (StationRecipes.accepts(1, getSlotIndex(), stack, client)) cir.setReturnValue(true);
    }
    @Inject(method = "getSlotStackLimit", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$multiple(CallbackInfoReturnable<Integer> cir) { cir.setReturnValue(64); }
    @Inject(method = "canHoldPotion", at = @At("RETURN"), cancellable = true)
    private static void cnpcplus$shift(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        boolean client = net.minecraftforge.fml.common.FMLCommonHandler.instance().getEffectiveSide().isClient();
        for (int i = 0; i < 3; i++) if (StationRecipes.accepts(1, i, stack, client)) { cir.setReturnValue(true); break; }
    }
}
