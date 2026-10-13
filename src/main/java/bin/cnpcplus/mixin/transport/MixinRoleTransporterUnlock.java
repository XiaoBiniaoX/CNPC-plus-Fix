package bin.cnpcplus.mixin.transport;

import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 传送点解锁后立即落盘，防止崩溃丢失。
 *
 * <p>RoleTransporter.unlock 只写内存不保存，唯一持久化靠原版 autosave（5min）。
 * 崩溃会丢失两次 autosave 之间的解锁记录。
 */
@Mixin(value = RoleTransporter.class, remap = false)
public class MixinRoleTransporterUnlock {

    @Inject(method = "unlock", at = @At("TAIL"), remap = false)
    private void cnpcplus$saveAfterUnlock(Player player, noppes.npcs.controllers.data.TransportLocation loc, CallbackInfo ci) {
        if (player == null || player.level().isClientSide) return;
        try {
            PlayerData.get(player).save(false);
        } catch (Exception ignored) {
        }
    }
}
