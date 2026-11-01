package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.lines.DialogArrays;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCDialogArrays {
    @Inject(method = "getDialog", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$priority(Player player, CallbackInfoReturnable<Dialog> cir) {
        for (DialogOption option : DialogArrays.ordered(((EntityNPCInterface)(Object)this).dialogs)) {
            if (!option.hasDialog()) continue;
            Dialog dialog = option.getDialog();
            if (dialog != null && dialog.availability.isAvailable(player)) {
                cir.setReturnValue(dialog);
                return;
            }
        }
        cir.setReturnValue(null);
    }
}
