package bin.cnpcplus.mixin.quest;

import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerQuestData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * checkQuestCompletion changes QuestData.isCompleted but never flags the player data
 * for a client sync, so the quest log only refreshes on a later request.
 * Its return value is already the exact "a quest just completed" signal: an already
 * completed quest is skipped (continue) before the flag is ever set.
 */
@Mixin(value = PlayerQuestData.class, remap = false)
public class MixinPlayerQuestData {
    @Inject(method = "checkQuestCompletion", at = @At("RETURN"), require = 1, remap = false)
    private void cnpcplus$syncOnCompletion(EntityPlayer player, int type,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || player == null || player.world.isRemote) {
            return;
        }
        PlayerData data = PlayerData.get(player);
        if (data != null) {
            data.updateClient = true;
        }
    }
}
