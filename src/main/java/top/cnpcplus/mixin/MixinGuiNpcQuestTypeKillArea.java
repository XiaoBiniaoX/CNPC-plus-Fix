package top.cnpcplus.mixin;

import noppes.npcs.client.gui.questtypes.GuiNpcQuestTypeKill;
import noppes.npcs.quests.QuestKill;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.QuestAreaKill;

@Mixin(value = GuiNpcQuestTypeKill.class, remap = false)
public abstract class MixinGuiNpcQuestTypeKillArea {
    @Shadow private QuestKill quest;

    @Inject(method = "saveTargets", at = @At("TAIL"), remap = false, require = 1)
    private void cnpcplus$restoreAreaFilter(CallbackInfo ci) {
        if (quest instanceof QuestAreaKill area) area.refreshTargets();
    }
}
