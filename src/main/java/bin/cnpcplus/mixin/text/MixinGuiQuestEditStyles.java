package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.TextStyles;
import bin.cnpcplus.text.client.SubGuiStyledTextEditor;
import bin.cnpcplus.text.client.PlayerTextPreview;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.global.GuiQuestEdit;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiQuestEdit.class, remap = false)
public abstract class MixinGuiQuestEditStyles extends SubGuiInterface {
    @Shadow private Quest quest;
    @Shadow private boolean questlogTA;
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$edit(GuiButton button, CallbackInfo ci) {
        if (button.id != 3 && button.id != 4) return;
        questlogTA = button.id == 4;
        final boolean log = questlogTA; String key = log ? TextStyles.LOG : TextStyles.COMPLETE;
        setSubGui(new SubGuiStyledTextEditor(TextStyles.get(quest, key, log ? quest.logText : quest.completeText), value -> {
            if (log) quest.logText = value.text; else quest.completeText = value.text;
            TextStyles.put(quest, key, value);
        }, value -> PlayerTextPreview.quest(quest, value, log))); ci.cancel();
    }
}
