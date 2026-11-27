package top.cnpcplus.mixin;

import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import noppes.npcs.client.gui.player.GuiQuestLog;
import org.spongepowered.asm.mixin.Mixin;
import top.cnpcplus.client.gui.QuestObjectiveScrollAccess;
import top.cnpcplus.quest.QuestCraft;

@Mixin(AbstractContainerEventHandler.class)
public abstract class MixinAbstractContainerEventHandlerQuestScroll implements ContainerEventHandler {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if ((Object)this instanceof GuiQuestLog gui && gui.selectedQuest != null
                && gui.selectedQuest.questInterface instanceof QuestCraft craft && delta != 0) {
            int count = 0;
            for (int i = 0; i < 9; i++) if (craft.configured(i)) count++;
            if (count > 3 && mouseX >= gui.guiLeft + 142 && mouseX < gui.guiLeft + 312
                    && mouseY >= gui.guiTop + 146 && mouseY < gui.guiTop + 178) {
                QuestObjectiveScrollAccess access = (QuestObjectiveScrollAccess)gui;
                access.cnpcplus$setCraftObjectiveScroll(Math.max(0,
                        Math.min(count - 3, access.cnpcplus$getCraftObjectiveScroll() + (delta > 0 ? -1 : 1))));
                return true;
            }
        }
        return ContainerEventHandler.super.mouseScrolled(mouseX, mouseY, delta);
    }
}
