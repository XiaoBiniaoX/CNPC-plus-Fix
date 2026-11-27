package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.QuestCraft;

import java.util.HashMap;
import java.util.Map;

/** Captures the exact custom cooking recipe before FurnaceResultSlot consumes its input. */
@Mixin(FurnaceResultSlot.class)
public abstract class MixinFurnaceResultSlotProduction {
    @Unique private Map<ResourceLocation, Integer> cnpcplus$usedRecipes = Map.of();

    @Inject(method = "onTake", at = @At("HEAD"), require = 1)
    private void cnpcplus$rememberCookingRecipe(Player player, ItemStack taken, CallbackInfo ci) {
        cnpcplus$usedRecipes = Map.of();
        if (player.level().isClientSide) return;
        FurnaceResultSlot slot = (FurnaceResultSlot)(Object)this;
        if (!(slot.container instanceof AbstractFurnaceBlockEntity furnace)) return;
        var recipes = ((AbstractFurnaceBlockEntityAccess)furnace).cnpcplus$getRecipesUsed();
        Map<ResourceLocation, Integer> snapshot = new HashMap<>();
        for (var entry : recipes.object2IntEntrySet()) if (entry.getIntValue() > 0)
            snapshot.put(entry.getKey(), entry.getIntValue());
        cnpcplus$usedRecipes = snapshot;
    }

    @Inject(method = "onTake", at = @At("TAIL"), require = 1)
    private void cnpcplus$postCookingProgress(Player player, ItemStack taken, CallbackInfo ci) {
        Map<ResourceLocation, Integer> completed = cnpcplus$usedRecipes;
        cnpcplus$usedRecipes = Map.of();
        if (player.level().isClientSide || completed.isEmpty()) return;
        FurnaceResultSlot slot = (FurnaceResultSlot)(Object)this;
        if (!(slot.container instanceof AbstractFurnaceBlockEntity furnace)) return;
        int remaining = taken.getCount();
        for (var entry : completed.entrySet()) {
            if (remaining <= 0) break;
            QuestCraft.RecipeReference recipe = QuestCraft.furnaceRecipe(entry.getKey().toString());
            if (recipe != null) {
                var data = top.cnpcplus.smelting.SmeltingRecipeRegistry.get(Integer.parseInt(recipe.id()));
                int produced = data == null ? 0 : Math.min(remaining,
                        QuestCraft.producedItems(entry.getValue(), data.output.getCount()));
                remaining -= produced;
                ItemProductionEvents.post(player, taken, ItemProductionEvent.Source.FURNACE,
                        player.level(), furnace.getBlockPos(), produced, recipe.station(), recipe.id());
            }
        }
    }
}
