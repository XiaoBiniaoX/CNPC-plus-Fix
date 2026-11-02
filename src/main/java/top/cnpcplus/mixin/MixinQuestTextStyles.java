package top.cnpcplus.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.text.TextStyles;

@Mixin(value = Quest.class, remap = false)
public abstract class MixinQuestTextStyles {
    @Shadow public String logText;
    @Shadow public String completeText;

    @Inject(method = "readNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$read(HolderLookup.Provider lookup, CompoundTag tag, CallbackInfo ci) {
        TextStyles.read(this, TextStyles.LOG, logText, tag);
        TextStyles.read(this, TextStyles.COMPLETE, completeText, tag);
    }

    @Inject(method = "writeToNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$save(HolderLookup.Provider lookup, CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        TextStyles.save(this, TextStyles.LOG, logText, cir.getReturnValue());
        TextStyles.save(this, TextStyles.COMPLETE, completeText, cir.getReturnValue());
    }
}
