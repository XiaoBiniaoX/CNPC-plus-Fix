package top.cnpcplus.mixin;

import noppes.npcs.client.gui.global.GuiQuestEdit;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.GuiStyledTextEditor;
import top.cnpcplus.text.client.PlayerTextPreview;

@Mixin(value = GuiQuestEdit.class, remap = false)
public abstract class MixinGuiQuestTextEditor {
    @Shadow private Quest quest;
    @Shadow private boolean questlogTA;

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$edit(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 3 && button.id != 4) return;
        questlogTA = button.id == 4;
        boolean log = questlogTA;
        String key = log ? TextStyles.LOG : TextStyles.COMPLETE;
        ((GuiQuestEdit) (Object) this).setSubGui(new GuiStyledTextEditor(TextStyles.get(quest, key, log ? quest.logText : quest.completeText), value -> {
            if (log) quest.logText = value.text(); else quest.completeText = value.text();
            TextStyles.put(quest, key, value);
        }, value -> PlayerTextPreview.quest(quest, value, log)));
        ci.cancel();
    }
}
