package top.cnpcplus.mixin;

import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.QuestAreaKill;
import top.cnpcplus.quest.QuestCraft;
import top.cnpcplus.quest.QuestExplore;

@Mixin(value = Quest.class, remap = false)
public abstract class MixinQuestTypeFactory {
    @Inject(method = "setType", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$createCustomQuestTypes(int type, CallbackInfo ci) {
        Quest quest = (Quest)(Object)this;
        if (type == 4) quest.questInterface = new QuestAreaKill();
        else if (type == 6) quest.questInterface = new QuestCraft();
        else if (type == 7) quest.questInterface = new QuestExplore();
        if (quest.questInterface != null && (type == 4 || type == 6 || type == 7)) {
            quest.questInterface.questId = quest.id;
        }
    }
}
