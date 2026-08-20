package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.common.MountTargetStore;
import noppes.npcs.ServerEventsHandler;
import noppes.npcs.CustomItems;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerEventsHandler.class, remap = false)
public class MixinServerEventsHandlerMount {
    @Inject(method = "invoke(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V", at = @At("TAIL"), remap = false)
    private void cnpcplus$rememberMountTarget(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        if (event.getEntityPlayer().getHeldItemMainhand().getItem() == CustomItems.mount) {
            MountTargetStore.put(event.getEntityPlayer(), event.getTarget());
        }
    }
}
