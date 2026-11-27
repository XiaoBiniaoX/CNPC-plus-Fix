package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.containers.SlotNpcCrafting;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.ProductionSlotAccess;

@Mixin(value = SlotNpcCrafting.class, remap = false)
public abstract class MixinCarpentryProduction {
    @org.spongepowered.asm.mixin.Unique private String cnpcplus$recipeId = "";

    @Inject(method = "onTake", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$rememberRecipe(Player player, ItemStack stack, CallbackInfo ci) {
        cnpcplus$recipeId = "";
        if (player.level().isClientSide || !(player.containerMenu instanceof ContainerCarpentryBench menu)
                || RecipeController.instance == null) return;
        RecipeCarpentry recipe = RecipeController.instance.findMatchingRecipe(menu.craftMatrix);
        if (recipe == null || recipe.getResult().isEmpty()) return;
        var id = bin.cnpcplus.recipe.id.RecipeIds.INSTANCE.locationOf(recipe);
        if (id == null) return;
        cnpcplus$recipeId = id.toString();
    }

    @Inject(method = "onTake", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$carpentryProduction(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide || !(player.containerMenu instanceof ContainerCarpentryBench menu)) return;
        ItemStack output = ((ProductionSlotAccess)this).cnpcplus$takeProduction(stack);
        if (cnpcplus$recipeId.isBlank()) return;
        ItemProductionEvents.post(player, output, ItemProductionEvent.Source.CARPENTRY,
                player.level(), ((CarpentryBenchAccess)menu).cnpcplus$pos(), output.getCount(),
                "carpentry", cnpcplus$recipeId);
        cnpcplus$recipeId = "";
    }
}
