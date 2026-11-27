package top.cnpcplus.mixin;

import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.QuestCraft;
import top.cnpcplus.quest.ProductionSlotAccess;

/** Both normal and Shift extraction pass here before vanilla clears removeCount. */
@Mixin(value = FurnaceResultSlot.class, remap = false)
public abstract class MixinFurnaceResultSlotProduction {
    @Shadow @Final private Player player;
    @Shadow private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"), require = 1)
    private void cnpcplus$takeProduction(ItemStack stack, CallbackInfo ci) {
        FurnaceResultSlot slot = (FurnaceResultSlot)(Object)this;
        if (player.level().isClientSide || removeCount <= 0
                || !(slot.container instanceof AbstractFurnaceBlockEntity furnace)) return;
        ItemStack output = ((ProductionSlotAccess)slot).cnpcplus$takeProduction(stack).copyWithCount(removeCount);
        var data = furnace.getPersistentData();
        var batches = data.getList("CNPCPlusFurnaceOutputs", Tag.TAG_COMPOUND);
        int total = 0;
        for (int i = 0; i < batches.size(); i++) total += batches.getCompound(i).getInt("Count");
        // Automation removes items without a player callback. Retire those units first.
        int discarded = Math.max(0, total - furnace.getItem(2).getCount() - removeCount);
        int remaining = removeCount;
        for (int i = 0; i < batches.size() && (discarded > 0 || remaining > 0);) {
            var batch = batches.getCompound(i);
            int count = batch.getInt("Count");
            int retired = Math.min(count, discarded);
            count -= retired;
            discarded -= retired;
            int taken = Math.min(count, remaining);
            count -= taken;
            remaining -= taken;
            var recipe = QuestCraft.furnaceRecipe(batch.getString("Recipe"));
            ItemStack produced = ItemStack.parseOptional(player.registryAccess(), batch.getCompound("Output"));
            if (recipe != null && taken > 0 && ItemStack.isSameItemSameComponents(output, produced)) ItemProductionEvents.post(player, output,
                    ItemProductionEvent.Source.FURNACE, player.level(), furnace.getBlockPos(),
                    taken, recipe.station(), recipe.id());
            if (count == 0) batches.remove(i);
            else { batch.putInt("Count", count); i++; }
        }
        data.put("CNPCPlusFurnaceOutputs", batches);
        furnace.setChanged();
    }
}
