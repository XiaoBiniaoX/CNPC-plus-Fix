package top.cnpcplus.mixin;

import noppes.npcs.client.gui.global.GuiQuestEdit;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.SubGuiQuestPicker;
import top.cnpcplus.client.gui.SubGuiQuestSettings;

@Mixin(value = GuiQuestEdit.class, remap = false)
public abstract class MixinGuiQuestEditTypes {
    private static final String[] TYPE_KEYS = {
            "quest.item", "quest.dialog", "quest.kill", "quest.location",
            "quest.areakill", "quest.manual", "cnpcplus.quest.craft", "cnpcplus.quest.explore"
    };
    private static final String[] REPEAT_KEYS = {
            "gui.no", "gui.yes", "quest.mcdaily", "quest.mcweekly", "quest.rldaily", "quest.rlweekly"
    };

    @Shadow private Quest quest;

    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$replaceArrows(CallbackInfo ci) {
        GuiNPCInterface gui = (GuiNPCInterface)(Object)this;
        GuiButtonNop type = gui.getButton(6);
        GuiButtonNop repeat = gui.getButton(8);
        if (type != null) { type.visible = false; type.shown = false; }
        if (repeat != null) { repeat.visible = false; repeat.shown = false; }
        GuiButtonNop typeButton = new GuiButtonNop(gui, 106, gui.guiLeft + 70, gui.guiTop + 88, 90, 20,
                TYPE_KEYS[Math.max(0, Math.min(quest.type, TYPE_KEYS.length - 1))]);
        GuiButtonNop repeatButton = new GuiButtonNop(gui, 108, gui.guiLeft + 70, gui.guiTop + 109, 140, 20,
                REPEAT_KEYS[quest.repeat.ordinal()]);
        gui.addButton(typeButton);
        gui.addButton(repeatButton);
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openQuestPanels(GuiButtonNop button, CallbackInfo ci) {
        GuiNPCInterface gui = (GuiNPCInterface)(Object)this;
        if (button.id == 106) {
            gui.setSubGui(new SubGuiQuestPicker(gui, quest, false));
            ci.cancel();
        } else if (button.id == 108) {
            gui.setSubGui(new SubGuiQuestPicker(gui, quest, true));
            ci.cancel();
        } else if (button.id == 7 && (quest.type == 2 || quest.type == 3 || quest.type == 4
                || quest.type == 5 || quest.type == 6 || quest.type == 7)) {
            gui.setSubGui(new SubGuiQuestSettings(quest));
            ci.cancel();
        }
    }
}
