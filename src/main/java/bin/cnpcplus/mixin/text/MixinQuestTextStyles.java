package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.TextStyles;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Quest.class, remap = false)
public abstract class MixinQuestTextStyles {
    @Shadow public String logText;
    @Shadow public String completeText;
    @Inject(method = "readNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$read(NBTTagCompound tag, CallbackInfo ci) {
        TextStyles.read(this, TextStyles.LOG, logText, tag); TextStyles.read(this, TextStyles.COMPLETE, completeText, tag);
    }
    @Inject(method = "writeToNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$write(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        TextStyles.write(this, TextStyles.LOG, logText, cir.getReturnValue()); TextStyles.write(this, TextStyles.COMPLETE, completeText, cir.getReturnValue());
    }
}
