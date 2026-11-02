package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.TextStyles;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Dialog.class, remap = false)
public abstract class MixinDialogTextStyles {
    @Shadow public String text;
    @Inject(method = "readNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$read(NBTTagCompound tag, CallbackInfo ci) { TextStyles.read(this, TextStyles.DIALOG, text, tag); }
    @Inject(method = "writeToNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$write(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) { TextStyles.write(this, TextStyles.DIALOG, text, cir.getReturnValue()); }
    @Inject(method = "copy", at = @At("RETURN"), require = 1)
    private void cnpcplus$copy(EntityPlayer player, CallbackInfoReturnable<Dialog> cir) {
        TextStyles.put(cir.getReturnValue(), TextStyles.DIALOG, TextStyles.get(this, TextStyles.DIALOG, text));
    }
}
