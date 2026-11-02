package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import noppes.npcs.ServerEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.clone.CloneMounts;

@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinClonerMountCapture {
    @Redirect(method = "invoke(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;m_20086_(Lnet/minecraft/nbt/CompoundTag;)Z"), require = 1)
    private boolean cnpcplus$captureWholeMount(Entity target, CompoundTag destination, PlayerInteractEvent.EntityInteract event) {
        try {
            CompoundTag captured = CloneMounts.capture(target);
            for (String key : captured.getAllKeys()) destination.put(key, captured.get(key));
            return true;
        } catch (Exception | LinkageError e) {
            event.getEntity().sendSystemMessage(Component.literal("复制失败，原克隆保留：" + e.getMessage()));
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[clone] 骑乘复制失败", e);
            return false;
        }
    }
}
