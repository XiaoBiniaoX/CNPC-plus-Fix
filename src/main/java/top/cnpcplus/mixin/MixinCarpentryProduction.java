package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.containers.SlotNpcCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;
import top.cnpcplus.quest.ProductionSlotAccess;

@Mixin(value = SlotNpcCrafting.class, remap = false)
public abstract class MixinCarpentryProduction {
    @Inject(method = "m_142406_", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$carpentryProduction(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide || !(player.containerMenu instanceof ContainerCarpentryBench menu)) return;
        ItemStack output = ((ProductionSlotAccess)this).cnpcplus$takeProduction(stack);
        ItemProductionEvents.post(player, output, ItemProductionEvent.Source.CARPENTRY,
                player.level(), ((CarpentryBenchAccess)menu).cnpcplus$pos(), output.getCount());
    }
}
