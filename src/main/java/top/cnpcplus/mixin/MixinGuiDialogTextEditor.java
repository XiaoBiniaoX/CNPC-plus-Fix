package top.cnpcplus.mixin;

import noppes.npcs.client.gui.global.GuiDialogEdit;
import noppes.npcs.controllers.data.Dialog;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.GuiStyledTextEditor;
import top.cnpcplus.text.client.PlayerTextPreview;
import noppes.npcs.client.NoppesUtil;

@Mixin(value = GuiDialogEdit.class, remap = false)
public class MixinGuiDialogTextEditor {
    @Shadow private Dialog dialog;

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$edit(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 3) return;
        ((GuiDialogEdit)(Object)this).setSubGui(new GuiStyledTextEditor(TextStyles.get(dialog, TextStyles.DIALOG, dialog.text), value -> {
            dialog.text = value.text();
            TextStyles.put(dialog, TextStyles.DIALOG, value);
        }, value -> PlayerTextPreview.dialog(NoppesUtil.getLastNpc(), dialog, value)));
        ci.cancel();
    }
}
