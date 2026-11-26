package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.ProductionSlotAccess;

@Mixin(SmithingMenu.class)
public abstract class MixinSmithingProduction {
    @Inject(method = "onTake", at = @At("TAIL"), require = 1)
    private void cnpcplus$smithingProduction(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide) return;
        SmithingMenu menu = (SmithingMenu)(Object)this;
        ItemStack output = ((ProductionSlotAccess)menu.getSlot(menu.getResultSlot())).cnpcplus$takeProduction(stack);
        ItemProductionEvents.post(player, output, ItemProductionEvent.Source.SMITHING,
                ((ItemCombinerMenuAccess)(Object)this).cnpcplus$access());
    }
}
