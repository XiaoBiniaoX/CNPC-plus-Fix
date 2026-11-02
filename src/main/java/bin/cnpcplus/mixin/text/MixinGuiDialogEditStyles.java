package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.TextStyles;
import bin.cnpcplus.text.client.SubGuiStyledTextEditor;
import bin.cnpcplus.text.client.PlayerTextPreview;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.global.GuiDialogEdit;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.controllers.data.Dialog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiDialogEdit.class, remap = false)
public abstract class MixinGuiDialogEditStyles extends SubGuiInterface {
    @Shadow private Dialog dialog;
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$edit(GuiButton button, CallbackInfo ci) {
        if (button.id != 3) return;
        setSubGui(new SubGuiStyledTextEditor(TextStyles.get(dialog, TextStyles.DIALOG, dialog.text), value -> {
            dialog.text = value.text; TextStyles.put(dialog, TextStyles.DIALOG, value);
        }, value -> PlayerTextPreview.dialog(npc, dialog, value))); ci.cancel();
    }
}
