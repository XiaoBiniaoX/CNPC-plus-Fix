package bin.cnpcplus.mixin.quest;

import noppes.npcs.NpcMiscInventory;
import noppes.npcs.quests.QuestItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enlarge the collect-item grid from 3 to 9. NpcMiscInventory grows the
 * backing list; NBT load uses per-slot keys, so old saves stay valid.
 */
@Mixin(value = QuestItem.class, remap = false)
public class MixinQuestItemGrid {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cnpcplus$enlargeItemGrid(CallbackInfo ci) {
        ((QuestItem) (Object) this).items = new NpcMiscInventory(9);
    }
}
