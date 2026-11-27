package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.recipe.id.RecipeIds;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.ProductionSlotAccess;
import top.cnpcplus.quest.QuestCraft;

@Mixin(value = ResultSlot.class, remap = false)
public abstract class MixinResultSlotProduction {
    @Shadow @Final private CraftingContainer craftSlots;

    @Inject(method = "onTake", at = @At("HEAD"), require = 1)
    private void cnpcplus$rememberRecipe(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide || !(player.containerMenu instanceof CraftingMenu menu)
                || player.containerMenu.slots.get(0) != (Object)this
                || !QuestCraft.isWorkbenchGrid(craftSlots.getContainerSize())) return;
        var found = player.level().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftSlots.asCraftInput(), player.level()).orElse(null);
        RecipeCarpentry recipe = found != null && found.value() instanceof RecipeCarpentry carpentry ? carpentry : null;
        if (recipe == null) return;
        var id = RecipeIds.INSTANCE.locationOf(recipe);
        if (id == null) return;
        ((top.cnpcplus.quest.ProductionSlotAccess)this).cnpcplus$rememberRecipe(
                "workbench", id.toString());
    }

    @Inject(method = "onTake", at = @At("TAIL"), require = 1)
    private void cnpcplus$craftProduction(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide || !(player.containerMenu instanceof CraftingMenu menu)
                || player.containerMenu.slots.get(0) != (Object)this) return;
        ProductionSlotAccess production = (ProductionSlotAccess)this;
        if (production.cnpcplus$recipeStation().isBlank()) return;
        ItemStack output = production.cnpcplus$takeProduction(stack);
        ItemProductionEvent.Source source = production.cnpcplus$recipeStation().equals("carpentry")
                ? ItemProductionEvent.Source.CARPENTRY : ItemProductionEvent.Source.WORKBENCH;
        ItemProductionEvents.post(player, output, source,
                ((CraftingMenuAccess)menu).cnpcplus$access(), output.getCount(),
                production.cnpcplus$recipeStation(), production.cnpcplus$recipeId());
        production.cnpcplus$clearRecipe();
    }
}
