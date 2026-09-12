package bin.cnpcplus.mixin.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.mixin.EntityLivingIMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家骑乘 NPC 且骑乘控制为开、导航为陆地时，按空格让 NPC 跳跃。
 *
 * <p>原版 {@code EntityNPCInterface.travel}（:1838-1862）在骑乘分支里只读乘客的
 * {@code xxa}（横向）和 {@code zza}（前后），垂直方向用的是 NPC 自己的 {@code travelVector.y}，
 * <b>从未读取玩家的 jumping</b>，所以空格没有任何作用。CNPC 也没有实现原版的
 * {@code PlayerRideableJumping} 接口（全库零命中）。
 *
 * <p>玩家的空格键在服务端是可靠可读的：客户端每 tick 发 {@code ServerboundPlayerInputPacket}
 * （LocalPlayer:242），服务端 {@code setPlayerInput} 会写入 {@code player.jumping}。
 * CNPC 自带 {@code EntityLivingIMixin} 这个 accessor 可以取到该字段，不需要新增网络包。
 *
 * <p>跳跃用 {@code jumpFromGround()} 而不是 {@code getJumpControl().jump()}：后者每 tick 会被
 * {@code Mob.serverAiStep} 清零，还会与 MoveControl 抢占。两端都执行，避免客户端算出的位移
 * 与服务端不一致导致回弹。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceMountJump {

    /** 原版 noJumpDelay 的同等语义，防止按住空格连跳刷高度。 */
    @Unique
    private int cnpcplus$jumpDelay = 0;

    @Inject(method = "travel", at = @At("HEAD"))
    private void cnpcplus$mountJump(Vec3 travelVector, CallbackInfo ci) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;

        if (this.cnpcplus$jumpDelay > 0) {
            this.cnpcplus$jumpDelay--;
        }

        if (self.ais == null) return;
        // 只管陆地导航：1 是飞行（空格另有升降含义），2 是水生。
        if (self.ais.movementType != 0) return;
        if (!self.ais.mountControl) return;
        if (!self.isAlive() || !self.isVehicle()) return;
        if (!self.onGround()) return;
        if (this.cnpcplus$jumpDelay > 0) return;

        LivingEntity rider = self.getControllingPassenger();
        if (!(rider instanceof Player)) return;
        if (!(rider instanceof EntityLivingIMixin access)) return;
        if (!access.jumping()) return;

        self.jumpFromGround();
        this.cnpcplus$jumpDelay = 10;
    }
}
