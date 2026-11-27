package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.ProductionSlotAccess;
import top.cnpcplus.quest.BrewingProductionAccess;

/** Slot.onTake is the common server-side boundary after an output has left its result slot. */
@Mixin(Slot.class)
public abstract class MixinSlotProduction implements ProductionSlotAccess {
    @Unique private ItemStack cnpcplus$quickOutput = ItemStack.EMPTY;
    @Unique private String cnpcplus$station = "";
    @Unique private String cnpcplus$recipeId = "";

    @Inject(method = "onQuickCraft(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$rememberQuickOutput(ItemStack remaining, ItemStack before, CallbackInfo ci) {
        cnpcplus$quickOutput = before.copyWithCount(before.getCount() - remaining.getCount());
    }

    @Override
    public ItemStack cnpcplus$takeProduction(ItemStack taken) {
        ItemStack output = cnpcplus$quickOutput.isEmpty() ? taken.copy() : cnpcplus$quickOutput;
        cnpcplus$quickOutput = ItemStack.EMPTY;
        return output;
    }

    @Override public void cnpcplus$rememberRecipe(String station, String recipeId) {
        cnpcplus$station = station == null ? "" : station;
        cnpcplus$recipeId = recipeId == null ? "" : recipeId;
    }
    @Override public String cnpcplus$recipeStation() { return cnpcplus$station; }
    @Override public String cnpcplus$recipeId() { return cnpcplus$recipeId; }
    @Override public void cnpcplus$clearRecipe() {
        cnpcplus$station = "";
        cnpcplus$recipeId = "";
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void cnpcplus$production(Player player, ItemStack stack, CallbackInfo ci) {
        ItemStack output = cnpcplus$takeProduction(stack);
        if (player.level().isClientSide || output.isEmpty()) return;
        AbstractContainerMenu menu = player.containerMenu;
        Slot slot = (Slot)(Object)this;
        int index = menu.slots.indexOf(slot);
        ItemProductionEvent.Source source = null;
        int count = output.getCount();
        String station = "", recipeId = "";
        if (menu instanceof BrewingStandMenu && index >= 0 && index < 3
                && slot.container instanceof BrewingProductionAccess brewing) {
            count = brewing.cnpcplus$takeBrewOutput(index, output);
            int id = count > 0 ? brewing.cnpcplus$takeBrewRecipe(index) : -1;
            if (count > 0 && id >= 0) {
                source = ItemProductionEvent.Source.BREWING;
                station = "brewing";
                recipeId = Integer.toString(id);
            }
        }
        if (source != null) {
            var pos = slot.container instanceof BlockEntity block ? block.getBlockPos() : player.blockPosition();
            ProductionSlotAccess production = (ProductionSlotAccess)slot;
            ItemProductionEvents.post(player, output, source, player.level(), pos, count, station, recipeId);
            production.cnpcplus$clearRecipe();
        }
    }
}
