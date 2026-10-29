package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.ai.selector.NPCAttackSelector;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.NpcAiOptimizerAccess;

/** 241h：廉价范围拒绝。 */
@Mixin(value = NPCAttackSelector.class, remap = false)
public abstract class MixinNpcFixAttackSelector {
    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Inject(method = "isEntityApplicable", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$cheapReject(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!NpcAiOptimizerAccess.enabled() || this.npc == null || entity == null
                || entity == this.npc || !entity.isAlive()) return;
        int maxRange = NpcAiOptimizerAccess.maxAggroRange();
        if (maxRange > 0 && !this.npc.isInRange(entity, maxRange)) cir.setReturnValue(false);
    }
}
