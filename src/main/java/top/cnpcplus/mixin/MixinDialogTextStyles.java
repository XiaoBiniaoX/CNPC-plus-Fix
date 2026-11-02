package top.cnpcplus.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.text.TextStyles;

@Mixin(value = Dialog.class, remap = false)
public abstract class MixinDialogTextStyles {
    @Shadow public String text;

    @Inject(method = "readNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$read(HolderLookup.Provider lookup, CompoundTag tag, CallbackInfo ci) {
        TextStyles.read(this, TextStyles.DIALOG, text, tag);
    }

    @Inject(method = "writeToNBTPartial", at = @At("RETURN"), require = 1)
    private void cnpcplus$save(HolderLookup.Provider lookup, CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        TextStyles.save(this, TextStyles.DIALOG, text, cir.getReturnValue());
    }

    @Inject(method = "copy", at = @At("RETURN"), require = 1)
    private void cnpcplus$copy(Player player, CallbackInfoReturnable<Dialog> cir) {
        TextStyles.put(cir.getReturnValue(), TextStyles.DIALOG, TextStyles.get(this, TextStyles.DIALOG, text));
    }
}
