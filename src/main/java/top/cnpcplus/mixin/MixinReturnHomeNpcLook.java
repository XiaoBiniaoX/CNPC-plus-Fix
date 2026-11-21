package top.cnpcplus.mixin;

import noppes.npcs.ai.EntityAILook;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.ReturnHomeState;

@Mixin(value = EntityAILook.class, remap = false)
public class MixinReturnHomeNpcLook {
    @Shadow @Final private EntityNPCInterface npc;
    @Shadow private boolean forced;

    @Inject(method = "m_8037_", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$deferStandingRotation(CallbackInfo ci) {
        if (!forced && npc.ais.getStandingType() == 1 && ReturnHomeState.target(npc) != null) ci.cancel();
    }
}
