package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.mixin.EntityLivingIMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/**
 * 玩家骑乘 NPC 且「骑乘控制」为开、导航为陆地时，按空格让 NPC 跳跃（哈基彬需求 B4）。
 *
 * <h3>原版为什么不能跳</h3>
 * 骑乘移动在 {@code EntityNPCInterface.m_7023_}（travel，反编译 1948-1979 行）。
 * 飞行分支（{@code :1963-1966}）会用视角俯仰算出垂直速度，而**陆地分支
 * （{@code :1967-1970}）把 {@code travelVector.y} 原样透传**，压根没有跳跃处理：
 * <pre>
 * super.m_7023_(new Vec3(f, travelVector.f_82480_, f1));
 * </pre>
 * 全库搜 {@code m_6135_}（jumpFromGround）**零命中** —— CNPC 从来没写过陆地跳跃。
 *
 * <h3>为什么注入 m_8107_ HEAD 而不是在 travel 里改 y</h3>
 * vanilla 的跳跃是这个顺序：
 * <pre>
 * Mob.m_8024_()（serverAiStep）尾部 → jumpControl.m_8124_() → mob.m_6862_(jump)
 * LivingEntity.m_8107_()（aiStep）的 jump 段 → 若 jumping &amp;&amp; onGround &amp;&amp; noJumpDelay==0
 *                                            → m_6135_() 起跳
 * ...再往后才是 m_7023_()（travel）
 * </pre>
 * 也就是说 <b>travel 阶段已经晚于 jump 段</b>，在那里 setJumping 这一 tick 不生效，
 * 而下一 tick 又会被 {@code jumpControl.tick()} 覆盖回去。
 *
 * <p>所以正确做法是在 jump 段**之前**告诉 jumpControl「要跳」。
 * {@code EntityNPCInterface.m_8107_} 第 634 行才调 {@code super.m_8107_()}，
 * 注入它的 HEAD 就一定早于 vanilla 的整套跳跃管线，
 * {@code jumpControl.tick()} 随后会把 {@code jumping} 置真，原版 jump 段自然起跳。
 *
 * <p>走原版管线的好处：自动尊重跳跃高度属性、跳跃提升药水、{@code noJumpDelay} 间隔，
 * 也不会和台阶自动跨越（骑乘时 {@code m_274367_(1.1f)}，{@code :1962}）打架。
 * 直接 setDeltaMovement 硬给一个 y 速度这些全都要自己重写。
 *
 * <h3>为什么只注 EntityNPCInterface</h3>
 * {@code EntityNPCFlying} 与 {@code EntityCustomNpc} 都没有覆写 {@code m_8107_}
 * （后者覆写的是 {@code m_8119_}），所以注基类即覆盖所有 NPC 类型。
 *
 * <p>与已有的 {@code MixinEntityNPCInterfaceStuckDeath}（同方法 HEAD）并存无冲突，
 * Mixin 允许同一注入点多个 handler。
 *
 * <h3>乘客跳跃状态怎么读</h3>
 * 用 CNPC 自带的 {@code noppes.npcs.mixin.EntityLivingIMixin#jumping()}
 * （{@code @Accessor("jumping")}）。本项目 {@code MixinEntityNPCFlyingFix} 已经在用
 * 同一个 accessor 做飞行升降，保持一致，不另造。
 *
 * <h3>为什么不碰飞行</h3>
 * 飞行的垂直控制已由 {@code MixinEntityNPCFlyingFix.cnpcplus$verticalFlightControl}
 * 处理（它 ModifyArg 的是 {@code m_7023_} 里 ordinal 0 那次 super 调用，即飞行分支）。
 * 这里用 {@code movementType == 0} 守卫，只管陆地，两者互不干扰。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCMountJump {

    @Unique
    private boolean cnpcplus$mountJumpHeld;

    @org.spongepowered.asm.mixin.Shadow(remap = false) public DataAI ais;

    @Inject(method = "m_7023_", at = @At("HEAD"), remap = false)
    private void cnpcplus$mountJump(net.minecraft.world.phys.Vec3 travelVector, CallbackInfo ci) {
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.MountJumpEnabled, true)) return;

        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        // 跳跃是服务端权威动作；客户端跑一遍只会造成位置抖动。
        if (npc.isClientSide()) return;

        // 只管陆地导航（0=陆地 / 1=飞行 / 2=游泳）。飞行由 MixinEntityNPCFlyingFix 负责。
        if (this.ais == null || this.ais.movementType != 0) return;
        if (!this.ais.mountControl) return;

        // getControllingPassenger 自带 mountControl 判断（反编译 :476-478），
        // 但这里已经判过了，拿它主要是为了取到「第一个乘客」这个语义。
        LivingEntity rider = npc.getControllingPassenger();
        if (rider == null) return;

        boolean jumping = ((EntityLivingIMixin) rider).jumping();
        if (!jumping) {
            this.cnpcplus$mountJumpHeld = false;
            return;
        }
        if (this.cnpcplus$mountJumpHeld) return;
        this.cnpcplus$mountJumpHeld = true;
        if (!npc.onGround()) return;

        // CNPC 自己的 m_8107_ 包装了 vanilla jump 段，JumpControl 在此处不可靠。
        // 直接调用 vanilla protected jumpFromGround，把空格转成真实垂直速度。
        ((LivingEntityJumpInvoker) (Object) npc).cnpcplus$jumpFromGround();
    }
}
