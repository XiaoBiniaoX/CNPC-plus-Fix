package bin.cnpcplus.mixin.quest;

import noppes.npcs.ServerEventsHandler;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerEventsHandler.class, remap = false)
public class MixinServerEventsHandler {

    @Inject(method = "pickUp", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void cnpcplus$deferQuestCheck(EntityItemPickupEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}
