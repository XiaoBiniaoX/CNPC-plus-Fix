package top.cnpcplus.mixin;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import noppes.npcs.ai.EntityAIReturn;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;
import top.cnpcplus.ai.ReturnHomeState;

/**
 * 修「返回起点时快到了却停顿一下再瞬移过去」（哈基彬需求 B2）。
 * 用户要求修改，原注释录 傻逼好鸟你他妈这个都要留是吗
 * <h3>症状与机制（反编译实证）</h3>
 * {@code EntityAIReturn.m_8037_}（tick，反编译 98-119 行）：
 * <pre>
 *  99-104  totalTicks &gt; 600  → m_6034_(endPos) + m_26573_()      ← 瞬移点 1（超时）
 * 105-106  stuckTicks &gt; 0    → --stuckTicks（这一刻什么都不做）
 * 107-109  navigation.m_26571_() → ++stuckCount; stuckTicks = 10   ← 「停顿」10 tick
 * 110-112  ... || stuckCount &gt; 5 → m_6034_(endPos) + m_26573_()   ← 瞬移点 2（卡住）
 * 113-115  否则 navigate(stuckCount % 2 == 1)
 * </pre>
 * 「快到起点」这个时机会同时满足两个条件：
 * <ul>
 *   <li>{@code navigation.isDone()} 为真 —— 路径已经走完；</li>
 *   <li>{@code isVeryNearAssignedPlace()} 仍为假 —— 它的阈值是
 *       {@code EntityNPCInterface.java:1537-1543} 里**硬编码的 ±0.2 格**，只判 x/z。
 *       而 {@code navigate()} 用 {@code moveTo(x, y, z, 1.0)}，陆地寻路自身的到达容差是
 *       {@code NpcGroundPathNavigator.java:54} 的 {@code 0.75 - width/2}（默认宽 0.6 → 0.45），
 *       比 0.2 宽一倍多，所以根本达不到。</li>
 * </ul>
 * 于是每次都被当成「卡住了」：先 {@code stuckTicks = 10} 冻结十刻（玩家看到的停顿），
 * 再重新寻路，而 {@code navigate(true)} 走的是 {@code DefaultRandomPos} **随机点**
 * （{@code :152-161}），更进不了 ±0.2；几个来回后 {@code stuckCount > 5} 成立 →
 * setPos 瞬移（玩家看到的跳跃）。整个过程发生在离起点不到一格的地方，所以特别违和。
 *
 * <h3>修法：拦多处调用点，只在「已经很接近起点」时改变行为</h3>
 * <ol>
 *   <li><b>{@code m_6034_}（setPos）</b> —— 两个瞬移点共用同一个方法，
 *       一个 Redirect 全覆盖。近起点则不瞬移并清零 stuck 计数；
 *       真的很远（卡墙里、掉洞里、地形不可达）照原样瞬移，保底机制不拆。</li>
 *   <li><b>{@code m_26573_}（PathNavigation.stop）</b> —— 原版紧跟在每次瞬移之后。
 *       若只跳过 setPos 而让 stop 照常执行，NPC 会因为没有路径干站在原地，
 *       等 600 tick 到了再被超时瞬移，症状只是换了个样子。
 *       所以抑制瞬移的那一刻要把 stop 换成「重新指向起点」。</li>
 *   <li><b>{@code m_26571_}（PathNavigation.isDone）</b> —— 这是「停顿」的源头判断。
 *       近起点且路径已走完时报告 false，就不会进入
 *       {@code ++stuckCount; stuckTicks = 10} 那一段，从根上消除停顿；
 *       同时就地补下一段路径，让它挪完最后这点距离。</li>
 * </ol>
 *
 * <h3>为什么不直接放宽 isVeryNearAssignedPlace 的阈值</h3>
 * 它被 {@code m_8036_}（canUse，{@code :82}）与 {@code m_8045_}
 * （canContinueToUse，{@code :89}）共用，还被 {@code EntityAIAnimation:37,99} 用来
 * 决定待机动画。放宽阈值会让 NPC 更早认为「到了」而停止返回，结果停在离起点几格外不动
 * —— 那是另一个 bug。所以只动瞬移与卡住判定，不动到达判定本身。
 *
 * <h3>命名方向（易错点）</h3>
 * {@code m_6034_} / {@code m_26573_} / {@code m_26571_} 都是 **MC 的成员**，
 * CNPC 发布 jar 已 reobf，所以在 {@code remap = false} 的 mixin 里 target 必须写 SRG 名。
 * 而 handler 方法体内直接调用 MC 成员则写开发名（mojmap），由 reobf 转换。
 * 两个方向相反，别混淆。
 *
 * <h3>为什么不用 @Shadow 拿 npc 之外的东西</h3>
 * {@code endPosX/Y/Z}、{@code stuckTicks}、{@code stuckCount}、{@code npc} 都是
 * {@code EntityAIReturn} **自己声明**的字段（非继承），@Shadow 可正常解析。
 * 「@Shadow 不能用于继承成员」那条限制不适用于本类自有字段。
 */
@Mixin(value = EntityAIReturn.class, remap = false)
public abstract class MixinEntityAIReturnSmoothArrival {

    @Unique
    private static final double CNPCPLUS_FINAL_SNAP_DISTANCE = 1.5D;

    @Shadow(remap = false) @Final private EntityNPCInterface npc;
    @Shadow(remap = false) private double endPosX;
    @Shadow(remap = false) private double endPosY;
    @Shadow(remap = false) private double endPosZ;
    @Shadow(remap = false) private int stuckTicks;
    @Shadow(remap = false) private int stuckCount;
    @Shadow(remap = false) private int totalTicks;
    @Shadow(remap = false) private boolean wasAttacked;

    /** 本刻是否刚抑制了一次瞬移。用于把紧随其后的 navigation.stop() 换成重新寻路。 */
    @Unique private boolean cnpcplus$suppressedTeleport;

    /**
     * 是否「已经足够接近起点，不该再瞬移」。
     *
     * 只比水平距离：竖直方向差异通常来自站在台阶/半砖上，不代表没走到。
     */
    @Unique
    private boolean cnpcplus$nearHome(EntityNPCInterface npc) {
        if (npc == null || !ServerConfigAccess.bool(CnpcPlusServerConfig.ReturnHomeSmoothArrival, true)) return false;
        double tolerance = ServerConfigAccess.decimal(CnpcPlusServerConfig.ReturnHomeArrivalTolerance, 3.0);
        double dx = npc.getX() - this.endPosX;
        double dz = npc.getZ() - this.endPosZ;
        return dx * dx + dz * dz <= tolerance * tolerance;
    }

    /** 重新把路径指向起点。 */
    @Unique
    private void cnpcplus$repath(EntityNPCInterface npc) {
        if (npc == null) return;
        PathNavigation nav = npc.getNavigation();
        if (nav == null) return;
        nav.moveTo(this.endPosX, this.endPosY, this.endPosZ, 1.0D);
    }

    @Inject(method = "m_8037_", at = @At("HEAD"), remap = false)
    private void cnpcplus$markReturnNavigationActive(CallbackInfo ci) {
        ReturnHomeState.start(this.npc);
    }

    @Inject(method = "m_8041_", at = @At("HEAD"), remap = false)
    private void cnpcplus$clearReturnNavigationActive(CallbackInfo ci) {
        ReturnHomeState.end(this.npc);
    }

    @Inject(method = "m_8037_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$walkFinalReturnGap(CallbackInfo ci) {
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.ReturnHomeSmoothArrival, true)
                || this.npc == null || this.npc.ais == null
                || this.npc.isAttacking() || this.npc.isInteracting()
                || this.npc.isKilled() || this.npc.isPassenger()) return;
        double dx = this.endPosX - this.npc.getX();
        double dz = this.endPosZ - this.npc.getZ();
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr <= 0.2D * 0.2D
                || distanceSqr > CNPCPLUS_FINAL_SNAP_DISTANCE * CNPCPLUS_FINAL_SNAP_DISTANCE) return;
        PathNavigation nav = this.npc.getNavigation();
        if (nav == null || !nav.isDone()) return;

        // Ground navigation ends its path near the block center. Keep the
        // return goal active and walk the remaining sub-block delta directly;
        // only the ±0.2 arrival condition below may finish this goal.
        nav.stop();
        this.stuckTicks = 0;
        this.stuckCount = 0;
        double speed = Math.min(1.0D, Math.max(0.1D, Math.sqrt(distanceSqr) * 1.5D));
        this.npc.getMoveControl().setWantedPosition(this.endPosX, this.endPosY, this.endPosZ, speed);
        ci.cancel();
    }

    /** 两处瞬移。近起点则跳过，并清掉 stuck 计数避免下一刻又被判卡住。 */
    @Redirect(method = "m_8037_",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;m_6034_(DDD)V"),
            remap = false)
    private void cnpcplus$smoothArrival(EntityNPCInterface npc, double x, double y, double z) {
        if (!cnpcplus$nearHome(npc)) {
            this.cnpcplus$suppressedTeleport = false;
            if (npc != null) npc.setPos(x, y, z);
            return;
        }
        int timeoutTicks = ServerConfigAccess.integer(CnpcPlusServerConfig.ReturnHomeTimeoutSeconds, 30) * 20;
        if (this.totalTicks > timeoutTicks) {
            this.cnpcplus$suppressedTeleport = false;
            this.stuckTicks = 0;
            this.stuckCount = 0;
            if (npc != null) npc.setPos(x, y, z);
            return;
        }
        // 抑制瞬移：清零两个 stuck 计数，让它按正常寻路走完最后这一段。
        this.cnpcplus$suppressedTeleport = true;
        this.stuckTicks = 0;
        this.stuckCount = 0;
    }

    /** 紧跟瞬移之后的 navigation.stop()。抑制过瞬移就换成重新寻路，否则照原样停。 */
    @Redirect(method = "m_8037_",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_26573_()V"),
            remap = false)
    private void cnpcplus$keepWalking(PathNavigation nav) {
        if (this.cnpcplus$suppressedTeleport) {
            this.cnpcplus$suppressedTeleport = false;
            if (nav != null) {
                nav.moveTo(this.endPosX, this.endPosY, this.endPosZ, 1.0D);
            }
            return;
        }
        if (nav != null) nav.stop();
    }

    /**
     * 「停顿」的源头：isDone() 为真才会 {@code ++stuckCount; stuckTicks = 10}。
     *
     * 近起点时路径走完是**正常现象**而不是卡住，所以报告 false 并就地补一段路径。
     * 这样既不冻结十刻，也不会因为没路径而站住。
     *
     * <h3>238-5a 死带修复（哈基彬报「到附近寻路就终止、回不准确」）</h3>
     * 原逻辑「就地 repath」有死带：moveTo 终点取整格节点、到达精度 0.45，够不到
     * {@code isVeryNearAssignedPlace} 的 ±0.2 —— done↔repath 无限打转，goal 永不
     * 结束 → 坐/躺永不恢复（5b）、昼夜切换被 MOVE 锁死（5c）同源。改为：
     * 近起点且路径走完时，不把 1.5 格以内剩余距离当作已到：重新寻路继续走；
     * 到达 ±0.2 后才让 canContinueToUse 正常结束。
     */
    @Redirect(method = "m_8037_",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_26571_()Z"),
            remap = false)
    private boolean cnpcplus$notStuckNearHome(PathNavigation nav) {
        boolean done = nav != null && nav.isDone();
        if (!done) return false;
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.ReturnHomeSmoothArrival, true)) return true;
        if (this.npc != null) {
            double dx = this.npc.getX() - this.endPosX;
            double dz = this.npc.getZ() - this.endPosZ;
            double distanceSqr = dx * dx + dz * dz;
            if (distanceSqr <= 0.2D * 0.2D) {
                this.stuckTicks = 0;
                this.stuckCount = 0;
                return false;
            }
            if (distanceSqr <= CNPCPLUS_FINAL_SNAP_DISTANCE * CNPCPLUS_FINAL_SNAP_DISTANCE) {
                // PathNavigation considers the last node reached early. Keep
                // the return goal active and compute another path to endPos.
                this.stuckTicks = 0;
                this.stuckCount = 0;
                cnpcplus$repath(this.npc);
                return false;
            }
        }
        cnpcplus$repath(this.npc);
        return false;
    }

    /**
     * 238c（#5）：canContinueToUse 的 isDone 早退——路径走完但没贴齐 ±0.2 就结束 goal
     * （陆地到达容差 0.45 &gt; 0.2），停在 1 格内、坐姿不复原（EntityAIAnimation 判
     * isVeryNearAssignedPlace）。近起点且未贴齐时报告「未走完」，让 goal 继续交给
     * tick 分支贴点/续寻路；贴齐后走 offset 34 的 isVeryNearAssignedPlace 早退正常
     * 结束；超距或关 config 照原版。m_8045_ 内 m_26571_ 唯一（字节码 offset 69；
     * :77 那处在 m_8037_，不冲突）。
     */
    @Redirect(method = "m_8045_",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_26571_()Z"),
            remap = false)
    private boolean cnpcplus$keepGoingUntilArrived(PathNavigation nav) {
        boolean done = nav != null && nav.isDone();
        // This replaces only the nav.isDone() operand in the original
        // ternary: a live path must evaluate false here so canContinue falls
        // through to the timeout check and remains true.
        if (!done) return false;
        if (this.npc != null && cnpcplus$nearHome(this.npc) && !this.npc.isVeryNearAssignedPlace()) {
            // Keep the goal alive so the HEAD handler can close CNPC's final
            // navigation-tolerance gap and the normal stop lifecycle runs.
            return false;
        }
        return true;
    }

    @Inject(method = "m_8045_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$preventNearHomeEarlyExit(CallbackInfoReturnable<Boolean> cir) {
        if (this.npc == null || this.npc.ais == null) return;
        if (!ServerConfigAccess.bool(CnpcPlusServerConfig.ReturnHomeSmoothArrival, true)) return;
        if (this.npc.isFollower() || this.npc.isKilled() || this.npc.isAttacking()
                || this.npc.isInteracting() || this.npc.isPassenger()) {
            cir.setReturnValue(false);
            return;
        }
        double dx = this.npc.getX() - this.endPosX;
        double dz = this.npc.getZ() - this.endPosZ;
        double distanceSqr = dx * dx + dz * dz;
        int timeoutTicks = ServerConfigAccess.integer(CnpcPlusServerConfig.ReturnHomeTimeoutSeconds, 30) * 20;
        boolean timedOut = this.totalTicks > timeoutTicks;
        if (timedOut) {
            this.stuckTicks = 0;
            this.stuckCount = 0;
            this.npc.setPos(this.endPosX, this.endPosY, this.endPosZ);
            cir.setReturnValue(false);
            return;
        }
        if (distanceSqr <= 0.2D * 0.2D) {
            cir.setReturnValue(false);
            return;
        }
        // This runs before vanilla's isVeryNearAssignedPlace early return.
        // Keep an active return goal alive until its actual endPos is reached;
        // during attacked returns that target can be the pre-attack position.
        cir.setReturnValue(true);
    }

    /**
     * 让超时瞬移的时间可以在 config 里改（需求 B2 的第二半）。
     *
     * 原版把 600 tick 硬编码在两处比较里：{@code m_8037_:100}（{@code totalTicks > 600}
     * → 瞬移）与 {@code m_8045_:94}（{@code return totalTicks <= 600}）。
     * 两处必须换成同一个值，否则会出现「AI 已放弃执行但还没瞬移」或反之的错位。
     *
     * 默认值与原版一致（30 秒 = 600 ticks），所以不改 config 就是原版行为。
     */
    @ModifyConstant(method = {"m_8037_", "m_8045_"},
            constant = @Constant(intValue = 600), remap = false)
    private int cnpcplus$configurableTimeout(int original) {
        return ServerConfigAccess.integer(CnpcPlusServerConfig.ReturnHomeTimeoutSeconds, 30) * 20;
    }

    @Inject(method = "m_8041_", at = @At("RETURN"), remap = false)
    private void cnpcplus$restoreConfiguredAnimation(CallbackInfo ci) {
        if (this.npc == null || this.npc.ais == null
                || this.npc.isAttacking() || this.npc.isInteracting() || this.npc.isKilled()) return;
        // EntityAIAnimation normally performs this transition after Return
        // releases MOVE.  Do it here as well so a near-home early-exit cannot
        // leave the NPC in the temporary walking animation forever.
        this.npc.setCurrentAnimation(this.npc.ais.getAnimation());
        this.npc.refreshDimensions();
        this.npc.setPos(this.npc.getX(), this.npc.getY(), this.npc.getZ());
        this.npc.updateAI = true;
    }
}
