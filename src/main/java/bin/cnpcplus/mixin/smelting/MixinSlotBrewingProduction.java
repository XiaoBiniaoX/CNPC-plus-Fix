package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.quest.CraftStation;
import bin.cnpcplus.quest.ItemProductionEvents;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class MixinSlotBrewingProduction {
    @Unique private ItemStack cnpcplus$taken = ItemStack.EMPTY;
    @Unique private String cnpcplus$recipe = "";

    @Unique private void cnpcplus$capture(ItemStack stack, int count) {
        Slot slot = (Slot)(Object)this;
        if (!(slot.inventory instanceof TileEntityBrewingStand) || slot.getSlotIndex() > 2) return;
        TileEntityBrewingStand tile = (TileEntityBrewingStand)slot.inventory;
        if (tile.getWorld().isRemote) return;
        NBTTagCompound record = ((IBrewingOutputRecipe)tile).cnpcplus$getProduced(slot.getSlotIndex());
        ItemStack produced = new ItemStack(record);
        if (!bin.cnpcplus.quest.QuestCraft.matches(produced, stack)) return;
        int amount = Math.min(count, produced.getCount());
        cnpcplus$taken = produced.copy(); cnpcplus$taken.setCount(amount);
        cnpcplus$recipe = record.getString("Recipe");
        record.setByte("Count", (byte)(produced.getCount() - amount));
    }
    @Inject(method = "decrStackSize", at = @At("HEAD"))
    private void cnpcplus$normal(int count, CallbackInfoReturnable<ItemStack> cir) {
        cnpcplus$capture(((Slot)(Object)this).getStack(), count);
    }
    @Inject(method = "onSlotChange", at = @At("HEAD"))
    private void cnpcplus$shift(ItemStack remaining, ItemStack original,
                                org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        cnpcplus$capture(original, original.getCount() - remaining.getCount());
    }
    @Inject(method = "onTake(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;", at = @At("RETURN"))
    private void cnpcplus$publishBrew(net.minecraft.entity.player.EntityPlayer player, ItemStack stack,
                                      CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = (Slot)(Object)this;
        if (cnpcplus$taken.isEmpty()) return;
        TileEntityBrewingStand tile = (TileEntityBrewingStand)slot.inventory;
        ItemProductionEvents.post(player, cnpcplus$taken, CraftStation.BREWING, tile.getPos(), cnpcplus$taken.getCount(), cnpcplus$recipe);
        cnpcplus$taken = ItemStack.EMPTY;
    }
}
