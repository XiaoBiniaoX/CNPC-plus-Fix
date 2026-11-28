package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotFurnaceOutput;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import bin.cnpcplus.quest.CraftStation;
import bin.cnpcplus.quest.ItemProductionEvents;
import bin.cnpcplus.quest.FurnaceProduction;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a recipe grant an experience value of 1 or more.
 *
 * SlotFurnaceOutput.onCrafting only scales by the experience value when it is
 * below 1.0 (bytecode offsets 56..59 jump straight past the scaling when
 * xp >= 1.0f), leaving the orb count equal to the number of items taken. Vanilla
 * gets away with that because every vanilla smelting recipe awards a fraction,
 * so any custom value of 1 or more collapsed to "one point per item" no matter
 * how large it was.
 *
 * Only recipes from this mod are touched; anything else falls through to the
 * vanilla path untouched.
 */
@Mixin(SlotFurnaceOutput.class)
public abstract class MixinSlotFurnaceOutputSmelting extends Slot {
    protected MixinSlotFurnaceOutputSmelting(IInventory inventory, int index, int x, int y) {
        super(inventory, index, x, y);
    }

    @Shadow private EntityPlayer player;
    @Shadow private int removeCount;

    @Unique private NBTTagList cnpcplus$taken;

    @Inject(method = "decrStackSize", at = @At("HEAD"))
    private void cnpcplus$beforeTake(int amount, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<ItemStack> cir) {
        if (player.world.isRemote) return;
        TileEntityFurnace furnace = (TileEntityFurnace)inventory;
        cnpcplus$taken = FurnaceProduction.take(FurnaceProduction.records(furnace, getStack().getCount()).copy(), amount);
    }

    @Inject(method = "onCrafting(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"),
            cancellable = true)
    private void cnpcplus$customExperience(ItemStack stack, CallbackInfo ci) {
        if (!player.world.isRemote && removeCount > 0) {
            TileEntityFurnace furnace = (TileEntityFurnace)inventory;
            NBTTagList taken = cnpcplus$taken;
            cnpcplus$taken = null;
            if (taken == null) taken = FurnaceProduction.take(
                    FurnaceProduction.records(furnace, getStack().getCount() + removeCount), removeCount);
            for (int i = 0; i < taken.tagCount(); i++) {
                NBTTagCompound record = taken.getCompoundTagAt(i);
                if (!record.getString("Recipe").isEmpty()) ItemProductionEvents.post(player, stack, CraftStation.FURNACE,
                        furnace.getPos(), record.getInteger("Count"), record.getString("Recipe"));
            }
        }
        if (stack == null || stack.isEmpty() || this.removeCount <= 0) {
            return;
        }
        SmeltingRecipeData data = findByOutput(stack);
        // Below 1 the vanilla maths is already correct, so leave it alone.
        if (data == null || data.xp < 1.0F) {
            return;
        }
        int count = this.removeCount;
        this.removeCount = 0;
        stack.onCrafting(this.player.world, this.player, count);
        if (this.player.world.isRemote) {
            ci.cancel();
            return;
        }
        int total = (int) Math.floor((double) count * (double) data.xp);
        while (total > 0) {
            int split = EntityXPOrb.getXPSplit(total);
            total -= split;
            this.player.world.spawnEntity(new EntityXPOrb(this.player.world,
                    this.player.posX, this.player.posY + 0.5D, this.player.posZ + 0.5D, split));
        }
        net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .firePlayerSmeltedEvent(this.player, stack);
        ci.cancel();
    }

    private static SmeltingRecipeData findByOutput(ItemStack output) {
        for (SmeltingRecipeData data : bin.cnpcplus.smelting.SmeltingRecipeRegistry.list()) {
            if (data != null && SmeltingFuelRules.stackMatches(output, data.output)) {
                return data;
            }
        }
        return null;
    }
}
