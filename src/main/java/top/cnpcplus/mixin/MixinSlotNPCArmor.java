package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "noppes.npcs.containers.SlotNPCArmor", remap = false)
public class MixinSlotNPCArmor {

    @Inject(method = "m_5857_", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$allowAllItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
