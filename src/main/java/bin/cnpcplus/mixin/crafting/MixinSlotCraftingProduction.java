package bin.cnpcplus.mixin.crafting;

import bin.cnpcplus.quest.QuestProductionProgress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SlotCrafting.class)
public abstract class MixinSlotCraftingProduction {
    @Shadow private EntityPlayer player;
    @Shadow private InventoryCrafting craftMatrix;
    @Shadow private int amountCrafted;

    // Both normal removal and Shift reach this method before the counter is reset.
    @Inject(method = "onCrafting(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
    private void cnpcplus$production(ItemStack stack, CallbackInfo ci) {
        QuestProductionProgress.crafted(player, craftMatrix, stack, amountCrafted);
    }
}
