package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.craftingview.RecipeAvailability;
import bin.cnpcplus.station.*;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(TileEntityBrewingStand.class)
public abstract class MixinBrewingRecipes {
    @Shadow private NonNullList<ItemStack> brewingItemStacks;
    @Shadow private int brewTime;
    @Shadow private int fuel;
    @Unique private int cnpcplus$recipe = -1;
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$customTick(CallbackInfo ci) {
        TileEntityBrewingStand tile = (TileEntityBrewingStand)(Object)this;
        if (tile.getWorld().isRemote) return;
        ItemStack[] inputs = {brewingItemStacks.get(0), brewingItemStacks.get(1), brewingItemStacks.get(2), brewingItemStacks.get(3)};
        if (!StationRecipes.hasMatch(1, inputs) && cnpcplus$recipe < 0) return;
        StationRecipe recipe = StationRecipes.find(1, inputs, RecipeAvailability.operator(tile));
        // Bottle slots are both input and output: excess inputs must not be overwritten.
        if (recipe != null) for (int i = 0; i < 3; i++) if (inputs[i].getCount() != recipe.inputs[i].getCount()) { recipe = null; break; }
        if (recipe == null) { brewTime = 0; cnpcplus$recipe = -1; }
        else {
            if (fuel <= 0 && brewingItemStacks.get(4).getItem() == Items.BLAZE_POWDER) { brewingItemStacks.get(4).shrink(1); fuel = 20; }
            if (cnpcplus$recipe != recipe.id) { brewTime = 0; cnpcplus$recipe = recipe.id; }
            if (brewTime > 0) {
                if (--brewTime == 0) {
                    for (int i = 0; i < 3; i++) brewingItemStacks.set(i, recipe.outputs[i].copy());
                    brewingItemStacks.get(3).shrink(recipe.inputs[3].getCount()); cnpcplus$recipe = -1;
                    tile.getWorld().playEvent(1035, tile.getPos(), 0);
                }
            } else if (fuel > 0) { --fuel; brewTime = recipe.time; }
        }
        IBlockState state = tile.getWorld().getBlockState(tile.getPos()), next = state;
        if (state.getBlock() instanceof BlockBrewingStand) {
            for (int i = 0; i < 3; i++) next = next.withProperty(BlockBrewingStand.HAS_BOTTLE[i], !brewingItemStacks.get(i).isEmpty());
            if (next != state) tile.getWorld().setBlockState(tile.getPos(), next, 2);
        }
        tile.markDirty(); ci.cancel();
    }
    @Inject(method = "isItemValidForSlot", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$input(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot < 4 && StationRecipes.accepts(1, slot, stack, ((TileEntityBrewingStand)(Object)this).getWorld().isRemote)) cir.setReturnValue(true);
    }
    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void cnpcplus$load(NBTTagCompound tag, CallbackInfo ci) {
        cnpcplus$recipe = tag.hasKey("CNPCPlusBrewRecipe") ? tag.getInteger("CNPCPlusBrewRecipe") : -1;
        if (cnpcplus$recipe >= 0) brewTime = tag.getInteger("CNPCPlusBrewTime");
    }
    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$save(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        tag.setInteger("CNPCPlusBrewRecipe", cnpcplus$recipe); tag.setInteger("CNPCPlusBrewTime", brewTime);
    }
}
