package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.ProductionSlotAccess;
import top.cnpcplus.smithing.CustomSmithingRecipe;

@Mixin(value = SmithingMenu.class, remap = false)
public abstract class MixinSmithingProduction {
    @Shadow private RecipeHolder<SmithingRecipe> selectedRecipe;
    @Unique private String cnpcplus$recipeId = "";

    @Inject(method = "onTake", at = @At("HEAD"), require = 1)
    private void cnpcplus$rememberSmithingRecipe(Player player, ItemStack stack, CallbackInfo ci) {
        cnpcplus$recipeId = "";
        if (!player.level().isClientSide && selectedRecipe != null && selectedRecipe.value() instanceof CustomSmithingRecipe) {
            String path = selectedRecipe.id().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            if (id.matches("\\d+")) cnpcplus$recipeId = id;
        }
    }

    @Inject(method = "onTake", at = @At("TAIL"), require = 1)
    private void cnpcplus$smithingProduction(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide) return;
        SmithingMenu menu = (SmithingMenu)(Object)this;
        ItemStack output = ((ProductionSlotAccess)menu.getSlot(menu.getResultSlot())).cnpcplus$takeProduction(stack);
        if (!cnpcplus$recipeId.isBlank()) ItemProductionEvents.post(player, output, ItemProductionEvent.Source.SMITHING,
                ((ItemCombinerMenuAccess)(Object)this).cnpcplus$access(), "smithing", cnpcplus$recipeId);
        cnpcplus$recipeId = "";
    }
}
