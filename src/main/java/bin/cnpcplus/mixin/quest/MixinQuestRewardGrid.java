package bin.cnpcplus.mixin.quest;

import noppes.npcs.NpcMiscInventory;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enlarge the reward grid from 9 to 15. NpcMiscInventory grows the backing
 * list; NBT load uses per-slot keys, so old saves stay valid.
 */
@Mixin(value = Quest.class, remap = false)
public class MixinQuestRewardGrid {

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cnpcplus$enlargeRewardGrid(CallbackInfo ci) {
        ((Quest) (Object) this).rewardItems = new NpcMiscInventory(15);
    }
}
