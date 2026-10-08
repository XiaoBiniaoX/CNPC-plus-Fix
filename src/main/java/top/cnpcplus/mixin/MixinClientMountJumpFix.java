package top.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/**
 * 客户端骑乘跳跃（根因见 2026-10-08 findings）：
 * 被骑实体的物理是客户端权威——vanilla {@code travelRidden} 只在
 * {@code isControlledByLocalInstance()}（即骑手.isLocalPlayer()）为真时才跑
 * {@code travel()}，服务端分支只做 setDeltaMovement 且不 move；
 * 而客户端不跑 serverAiStep/JumpControl（isEffectiveAi()==!isClientSide），
 * 没人给客户端侧 NPC 置 jumping 标志 → jumpFromGround 永不触发 → 不发载具包 → 全端无跳跃。
 * 这里把本地玩家的空格每 tick 同步进客户端侧 NPC 的 jumping，
 * vanilla aiStep 的 jump 段即可完成起跳，位移经 ServerboundMoveVehiclePacket 同步回服务端。
 */
@Mixin(value = KeyboardInput.class, remap = false)
public abstract class MixinClientMountJumpFix {

    @Unique
    private EntityNPCInterface cnpcplus$lastJumpNpc;

    @Inject(method = {"tick", "m_214106_"}, at = @At("RETURN"), remap = false)
    private void cnpcplus$clientMountJump(boolean flag, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) return;
        Input input = (Input) (Object) this;
        Entity vehicle = mc.player.getVehicle();
        if (vehicle instanceof EntityNPCInterface npc) {
            // 与服务端 MixinServerPlayerMountJump 的判定完全一致；
            // cfg 在多人下读不到服务端值时回落默认 true（ConfigSync 尚未实现）。
            boolean cfg = ServerConfigAccess.bool(CnpcPlusServerConfig.MountJumpEnabled, true);
            boolean ok = cfg
                    && npc.ais != null
                    && npc.ais.mountControl
                    && npc.ais.movementType == 0
                    && npc.getControllingPassenger() == mc.player;
            boolean jump = ok && input.jumping;
            npc.setJumping(jump);
            cnpcplus$lastJumpNpc = jump ? npc : null;
        } else if (cnpcplus$lastJumpNpc != null) {
            // 下坐/换载具时补 false，防止 jumping 卡 true 变成自动蹦跳。
            cnpcplus$lastJumpNpc.setJumping(false);
            cnpcplus$lastJumpNpc = null;
        }
    }
}
