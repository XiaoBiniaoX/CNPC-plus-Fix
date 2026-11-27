package top.cnpcplus.mixin;

import noppes.npcs.client.gui.questtypes.GuiNpcQuestTypeDialog;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcQuestTypeDialog.class, remap = false)
public abstract class MixinGuiNpcQuestTypeDialogLayout {
    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void cnpcplus$alignDialogOptions(CallbackInfo ci) {
        GuiNPCInterface gui = (GuiNPCInterface)(Object)this;
        for (int i = 0; i < 3; i++) {
            GuiButtonNop remove = gui.getButton(9 + i);
            GuiButtonNop select = gui.getButton(3 + i);
            int y = gui.guiTop + 55 + i * 22;
            if (remove != null) remove.setY(y);
            if (select != null) select.setY(y);
        }
    }
}
