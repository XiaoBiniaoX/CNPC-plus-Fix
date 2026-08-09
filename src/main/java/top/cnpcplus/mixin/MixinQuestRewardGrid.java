package top.cnpcplus.mixin;

import noppes.npcs.NpcMiscInventory;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Quest.class, remap = false)
public class MixinQuestRewardGrid {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cnpcplus$enlargeRewardGrid(CallbackInfo ci) {
        ((Quest) (Object) this).rewardItems = new NpcMiscInventory(15);
    }
}
