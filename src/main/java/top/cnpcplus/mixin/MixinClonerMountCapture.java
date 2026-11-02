package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import noppes.npcs.CustomItems;
import noppes.npcs.ServerEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.clone.CloneMounts;

@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinClonerMountCapture {
    // The interaction packet is already sent before this client event. Consume it locally
    // so Minecraft does not also send UseItem and replace the clone GUI with the air list.
    @Inject(method = "invoke(Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V",
            at = @At("TAIL"), require = 1)
    private void cnpcplus$consumeClientCopy(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        if (event.getEntity().level().isClientSide && event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity().getMainHandItem().getItem() == CustomItems.cloner
                && !(event.getTarget() instanceof Player)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @Redirect(method = "invoke(Lnet/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;saveAsPassenger(Lnet/minecraft/nbt/CompoundTag;)Z"), require = 1)
    private boolean cnpcplus$captureWholeMount(Entity target, CompoundTag destination, PlayerInteractEvent.EntityInteract event) {
        try {
            CompoundTag captured = CloneMounts.capture(target);
            for (String key : captured.getAllKeys()) destination.put(key, captured.get(key));
            return true;
        } catch (Exception | LinkageError e) {
            event.getEntity().sendSystemMessage(Component.translatable("cnpcplus.clone.mountCopyFailed", String.valueOf(e.getMessage())));
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[clone] 骑乘复制失败", e);
            return false;
        }
    }
}
