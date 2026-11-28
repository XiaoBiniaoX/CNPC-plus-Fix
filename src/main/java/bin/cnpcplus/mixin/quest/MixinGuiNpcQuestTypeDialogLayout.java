package bin.cnpcplus.mixin.quest;

import noppes.npcs.client.gui.questtypes.GuiNpcQuestTypeDialog;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.SubGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcQuestTypeDialog.class, remap = false)
public abstract class MixinGuiNpcQuestTypeDialogLayout extends SubGuiInterface {
    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$alignDialogOptions(CallbackInfo ci) {
        title = "";
        addLabel(new GuiNpcLabel(100, "cnpcplus.quest.dialog.title", guiLeft + 12, guiTop + 12, 0x202020));
        for (int i = 0; i < 3; i++) {
            GuiNpcButton remove = getButton(9 + i), select = getButton(3 + i);
            remove.y = select.y = guiTop + 55 + i * 22;
        }
    }

}
