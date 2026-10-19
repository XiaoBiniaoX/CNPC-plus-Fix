package top.cnpcplus.mixin;

import noppes.npcs.EventHooks;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.NpcAiOptimizerAccess;

@Mixin(value = EventHooks.class, remap = false)
public abstract class MixinNpcFixEventHooks {
    @Inject(method = "onNPCTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$throttleNpcUpdate(EntityNPCInterface npc, CallbackInfo ci) {
        if (!NpcAiOptimizerAccess.enabled() || npc == null) return;
        if (!NpcAiOptimizerAccess.updateWhenScriptsDisabled()
                && !NpcAiOptimizerAccess.scriptsEnabled(npc)) {
            ci.cancel();
            return;
        }
        int interval = NpcAiOptimizerAccess.updateInterval();
        if (interval > 10 && !NpcAiOptimizerAccess.shouldRun(npc, interval)) ci.cancel();
    }
}
