package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.ProductionSlotAccess;

@Mixin(ResultSlot.class)
public abstract class MixinResultSlotProduction {
    @Inject(method = "onTake", at = @At("TAIL"), require = 1)
    private void cnpcplus$craftProduction(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide || !(player.containerMenu instanceof CraftingMenu menu)
                || player.containerMenu.slots.get(0) != (Object)this) return;
        ItemProductionEvents.post(player, ((ProductionSlotAccess)this).cnpcplus$takeProduction(stack), ItemProductionEvent.Source.WORKBENCH,
                ((CraftingMenuAccess)menu).cnpcplus$access());
    }
}
