package bin.cnpcplus.mixin.lines;

import java.util.HashMap;
import bin.cnpcplus.lines.DialogArrays;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedDialogArrays {
    @Inject(method = "nbtDialogs", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$save(HashMap<Integer, DialogOption> dialogs, CallbackInfoReturnable<NBTTagList> cir) {
        cir.setReturnValue(DialogArrays.save(dialogs));
    }
    @Inject(method = "getDialogs", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$read(NBTTagList list, CallbackInfoReturnable<HashMap<Integer, DialogOption>> cir) {
        cir.setReturnValue(DialogArrays.read(list));
    }
}
