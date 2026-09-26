package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/** 服务端直接消费骑乘者的空格输入，支持静止骑乘和边跑边跳。 */
@Mixin(value = ServerPlayer.class, remap = false)
public abstract class MixinServerPlayerMountJump {
    @Unique
    private boolean cnpcplus$mountJumpHeld;

    @Inject(method = {"setPlayerInput", "m_8980_"}, at = @At("RETURN"), remap = false)
    private void cnpcplus$consumeMountJump(float xxa, float zza, boolean jumping,
                                            boolean shiftKeyDown, CallbackInfo ci) {
        if (!jumping) {
            cnpcplus$mountJumpHeld = false;
            return;
        }
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.MountJumpEnabled, true)) return;

        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!(player.getVehicle() instanceof EntityNPCInterface npc)) {
            cnpcplus$mountJumpHeld = false;
            return;
        }
        if (npc.ais == null || !npc.ais.mountControl || npc.ais.movementType != 0
                || npc.getControllingPassenger() != player) {
            cnpcplus$mountJumpHeld = false;
            return;
        }
        if (cnpcplus$mountJumpHeld) return;
        cnpcplus$mountJumpHeld = true;

        // 与 CNPC 脚本 API EntityLivingWrapper.jump() 完全相同的原生入口：
        // this.entity.getJumpControl().jump()。JumpControl 会在 NPC 下一次 serverAiStep
        // 中把 jumping 交给 vanilla 的 jump 段，静止骑乘也不依赖 travel 被调用。
        npc.getJumpControl().jump();
    }
}
