package bin.cnpcplus.mixin.quest;

import bin.cnpcplus.quest.QuestAreaKill;
import bin.cnpcplus.quest.QuestCraft;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Quest.class, remap = false)
public abstract class MixinQuestTypeFactory {
    @Shadow public int id;
    @Shadow public int type;
    @Shadow public QuestInterface questInterface;

    @Inject(method = "setType", at = @At("RETURN"), require = 1, remap = false)
    private void cnpcplus$createCustomType(int type, CallbackInfo ci) {
        if (type == 4) questInterface = new QuestAreaKill();
        else if (type == 6) questInterface = new QuestCraft();
        else if (type == 7) questInterface = new bin.cnpcplus.quest.QuestExplore();
        if (type == 4 || type == 6 || type == 7) questInterface.questId = id;
    }
}
