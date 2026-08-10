package bin.cnpcplus.mixin.recipe;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Global recipes are injected into the vanilla RecipeManager, so the workbench
 * (CraftingMenu) matches them without any availability check. Clear the result
 * slot when the matched RecipeCarpentry is not available to this player.
 */
@Mixin(value = CraftingMenu.class, remap = false)
public abstract class MixinCraftingMenu {

    @Shadow(remap = false)
    private ResultContainer resultSlots;

    // NOTE: never give this shadow an initializer — Mixin writes shadow field
    // initializers back into the target class, which would overwrite
    // CraftingMenu.<init> (this.player = inventory.player) with null.
    @Shadow(remap = false)
    private Player player;

    @Inject(method = "slotsChanged", at = @At("RETURN"), remap = false)
    private void cnpcplusCheckAvailability(Container container, CallbackInfo ci) {
        try {
            if (this.resultSlots.getItem(0).isEmpty()) return;
            RecipeHolder<?> holder = this.resultSlots.getRecipeUsed();
            if (holder == null || !(holder.value() instanceof RecipeCarpentry rc)) return;
            if (rc.availability != null && !rc.availability.isAvailable(this.player)) {
                this.resultSlots.setItem(0, ItemStack.EMPTY);
                if (this.player instanceof ServerPlayer sp) {
                    AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
                    sp.connection.send(new ClientboundContainerSetSlotPacket(menu.containerId, menu.incrementStateId(), 0, ItemStack.EMPTY));
                }
            }
        } catch (Throwable t) {
            // never break vanilla crafting
        }
    }
}
