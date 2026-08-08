package top.cnpcplus.mixin;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(CraftingMenu.class)
public class MixinCraftingMenuAvailability {

    @Inject(method = {"m_150546_", "setResultSlot"}, at = @At("RETURN"), remap = false)
    private static void cnpcplus$enforceCraftingAvailability(AbstractContainerMenu menu, Level level, Player player, CraftingContainer craftSlots, ResultContainer resultSlots, CallbackInfo ci) {
        if (level.isClientSide) return;
        ItemStack result = resultSlots.getItem(0);
        if (result.isEmpty()) return;

        Optional<CraftingRecipe> opt = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craftSlots, level);
        if (opt.isEmpty()) return;
        CraftingRecipe recipe = opt.get();
        if (!(recipe instanceof RecipeCarpentry carpentry)) return;
        if (carpentry.availability.isAvailable(player)) return;

        resultSlots.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.getStateId(), 0, ItemStack.EMPTY));
        }
    }
}
