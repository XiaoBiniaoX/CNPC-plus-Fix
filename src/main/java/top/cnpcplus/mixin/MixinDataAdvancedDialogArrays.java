package top.cnpcplus.mixin;

import java.util.HashMap;
import net.minecraft.nbt.ListTag;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.lines.DialogArrays;

@Mixin(value = DataAdvanced.class, remap = false)
public class MixinDataAdvancedDialogArrays {
    @Inject(method = "nbtDialogs", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$saveOrdered(HashMap<Integer, DialogOption> dialogs, CallbackInfoReturnable<ListTag> cir) {
        cir.setReturnValue(DialogArrays.save(dialogs));
    }
    @Inject(method = "getDialogs", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$readOrdered(ListTag list, CallbackInfoReturnable<HashMap<Integer, DialogOption>> cir) {
        cir.setReturnValue(DialogArrays.read(list));
    }
}
