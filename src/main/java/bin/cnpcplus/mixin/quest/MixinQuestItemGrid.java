package bin.cnpcplus.mixin.quest;

import noppes.npcs.NpcMiscInventory;
import noppes.npcs.quests.QuestItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = QuestItem.class, remap = false)
public class MixinQuestItemGrid {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cnpcplus$enlargeItemGrid(CallbackInfo ci) {
        ((QuestItem) (Object) this).items = new NpcMiscInventory(9);
    }
}
