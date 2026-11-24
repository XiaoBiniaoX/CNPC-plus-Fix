package bin.cnpcplus.mixin.lifecycle;

import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcDeleteMount {
    @Inject(method = "delete", at = @At("HEAD"), require = 1)
    private void cnpcplus$detach(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface)(Object)this;
        npc.dismountRidingEntity();
        npc.removePassengers();
    }
}
