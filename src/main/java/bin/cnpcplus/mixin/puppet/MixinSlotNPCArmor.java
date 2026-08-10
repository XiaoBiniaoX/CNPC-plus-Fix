package bin.cnpcplus.mixin.puppet;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "noppes.npcs.containers.SlotNPCArmor", remap = false)
public class MixinSlotNPCArmor {

    @Inject(method = "func_75214_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$allowAllItems(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Boolean.TRUE);
    }
}
