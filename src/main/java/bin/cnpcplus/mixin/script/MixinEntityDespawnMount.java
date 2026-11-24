package bin.cnpcplus.mixin.script;

import net.minecraft.entity.Entity;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityWrapper.class, remap = false)
public abstract class MixinEntityDespawnMount {
    @Shadow protected Entity entity;
    @Inject(method = "despawn", at = @At("HEAD"), require = 1)
    private void cnpcplus$detach(CallbackInfo ci) {
        entity.dismountRidingEntity();
        entity.removePassengers();
    }
}
