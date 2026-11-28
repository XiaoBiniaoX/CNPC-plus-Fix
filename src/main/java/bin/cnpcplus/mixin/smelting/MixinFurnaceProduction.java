package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.quest.FurnaceProduction;
import bin.cnpcplus.smelting.SmeltingFuelRules;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TileEntityFurnace.class)
public abstract class MixinFurnaceProduction {
    @Unique private int cnpcplus$beforeOutput;
    @Unique private String cnpcplus$recipe;

    @Inject(method = "smeltItem", at = @At("HEAD"))
    private void cnpcplus$beforeSmelt(CallbackInfo ci) {
        TileEntityFurnace tile = (TileEntityFurnace)(Object)this;
        cnpcplus$beforeOutput = tile.getStackInSlot(2).getCount();
        SmeltingRecipeData recipe = SmeltingFuelRules.findForFurnace(tile);
        cnpcplus$recipe = recipe == null ? "vanilla" : Integer.toString(recipe.id);
    }

    @Inject(method = "smeltItem", at = @At("RETURN"))
    private void cnpcplus$afterSmelt(CallbackInfo ci) {
        TileEntityFurnace tile = (TileEntityFurnace)(Object)this;
        int produced = tile.getStackInSlot(2).getCount() - cnpcplus$beforeOutput;
        if (!tile.getWorld().isRemote && produced > 0)
            FurnaceProduction.append(FurnaceProduction.records(tile, cnpcplus$beforeOutput), cnpcplus$recipe, produced);
    }

    @Inject(method = "decrStackSize", at = @At("HEAD"))
    private void cnpcplus$remove(int slot, int count, CallbackInfoReturnable<ItemStack> cir) {
        TileEntityFurnace tile = (TileEntityFurnace)(Object)this;
        if (slot == 2 && !tile.getWorld().isRemote)
            FurnaceProduction.take(FurnaceProduction.records(tile, tile.getStackInSlot(2).getCount()), count);
    }

    @Inject(method = "removeStackFromSlot", at = @At("HEAD"))
    private void cnpcplus$removeAll(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == 2) ((TileEntityFurnace)(Object)this).getTileData().removeTag("CNPCPlusFurnaceProduction");
    }

    @Inject(method = "setInventorySlotContents", at = @At("HEAD"))
    private void cnpcplus$replace(int slot, ItemStack stack, CallbackInfo ci) {
        if (slot == 2) ((TileEntityFurnace)(Object)this).getTileData().removeTag("CNPCPlusFurnaceProduction");
    }
}
