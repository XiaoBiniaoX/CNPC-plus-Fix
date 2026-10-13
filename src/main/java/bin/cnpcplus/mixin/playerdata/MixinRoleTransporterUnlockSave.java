package bin.cnpcplus.mixin.playerdata;

import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.roles.RoleTransporter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 传送师解锁传送点后立即落盘（阶段 38，哈基彬要求）。
 * 原版 RoleTransporter.unlock 只改内存 data.transports，不调用 save，
 * 崩溃/竞态时解锁记录随 uuid.json 一起丢失。
 * unlock 是 private，在 HEAD 之后、add 之前无法拦截返回值，
 * 整个方法体在 TAIL 注入 save（仅当实际新增时由 unlock 内部条件保证，
 * 但 TAIL 无条件 save 也无害——save 是幂等快照）。
 */
@Mixin(value = RoleTransporter.class, remap = false)
public abstract class MixinRoleTransporterUnlockSave {

    @Inject(method = "unlock", at = @At("TAIL"))
    private void cnpcplus$saveAfterUnlock(EntityPlayer player, noppes.npcs.controllers.data.TransportLocation loc, CallbackInfo ci) {
        if (!player.world.isRemote) {
            PlayerData.get(player).save(false);
        }
    }
}
