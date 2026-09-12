package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.config.CnpcPlusConfig;
import noppes.npcs.ai.EntityAIReturn;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复 AI「返回起点」在快到起点时反复停顿、最后一下瞬移过去（还会原地起跳）的问题，
 * 并把超时瞬移的时限改成可配置。
 *
 * <p>成因：目标点是 {@code getStartXPos/ZPos}，即方块坐标 + 0.5，而
 * {@code isVeryNearAssignedPlace()} 要求 X/Z 误差都在 ±0.2 以内（EntityNPCInterface:1442-1448）。
 * 导航的到点判据宽松得多（{@code maxDistanceToWaypoint} 约 0.45），于是路径走完、
 * {@code navigation.isDone()} 为真，但落点与目标常差 0.2~0.45 —— 恰好卡在两个判据中间。
 * 于是 {@code canContinueToUse} 不结束，{@code tick()} 走进「疑似卡住」分支：
 * 停 10 tick、重新 {@code moveTo}、又立刻到点，反复抖动（EntityAIReturn:105-118）；
 * 重发 6 次后 {@code stuckCount > 5} 命中，直接 {@code setPos} 硬瞬移。
 * 反复重设移动目标还会把原版 MoveControl 推进 JUMPING 分支，看起来就是在起点前原地起跳。
 *
 * <p>修法：路径已走完且水平误差已小于导航精度时，做一次小于半格的精确对位。位移极小玩家看不出来，
 * 但能让 {@code isVeryNearAssignedPlace()} 立刻成立，goal 正常结束 —— 抖动、瞬移、起跳一起消失。
 * 真正走不到（被挡住）时不再因为 {@code stuckCount} 瞬移，只保留超时兜底，时限由配置项决定。
 */
@Mixin(value = EntityAIReturn.class, remap = false)
public abstract class MixinEntityAIReturn {

    @Shadow
    @Final
    private EntityNPCInterface npc;

    @Shadow
    private int stuckTicks;

    @Shadow
    private int totalTicks;

    @Shadow
    private double endPosX;

    @Shadow
    private double endPosY;

    @Shadow
    private double endPosZ;

    @Shadow
    private int stuckCount;

    @Shadow
    private boolean wasAttacked;

    @Shadow
    protected abstract boolean isTooFar();

    @Shadow
    protected abstract void navigate(boolean towards);

    /** 配置的超时瞬移时限，换算成 tick。 */
    private static int cnpcplus$timeoutTicks() {
        return Math.max(20, CnpcPlusConfig.RETURN_START_TIMEOUT_SECONDS.get() * 20);
    }

    /**
     * 已经贴到起点时直接判定到位。
     *
     * <p>原版 {@code canContinueToUse} 的超时判断也写着 600，必须和 tick 里的时限同源，
     * 否则超时后 goal 先被判 false、tick 里的兜底瞬移永远等不到执行。
     */
    @Inject(method = "canContinueToUse", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$continueTimeout(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (this.totalTicks > cnpcplus$timeoutTicks()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$smoothReturn(CallbackInfo ci) {
        if (this.npc == null) return;

        ++this.totalTicks;

        // 超时兜底：真的走不回去（被封死、地形不通）才瞬移，时限可配。
        if (this.totalTicks > cnpcplus$timeoutTicks()) {
            this.npc.setPos(this.endPosX, this.endPosY, this.endPosZ);
            this.npc.getNavigation().stop();
            ci.cancel();
            return;
        }

        if (this.stuckTicks > 0) {
            --this.stuckTicks;
            ci.cancel();
            return;
        }

        if (!this.npc.getNavigation().isDone()) {
            // 正常走路中。
            this.stuckCount = 0;
            ci.cancel();
            return;
        }

        // 路径走完了。先看是不是已经到了「导航认为到了、但 isVeryNearAssignedPlace 还差一点」那个夹缝。
        double dx = this.npc.getX() - this.endPosX;
        double dz = this.npc.getZ() - this.endPosZ;
        if (dx * dx + dz * dz <= 1.0) {
            // 误差在一格以内：做一次不足半格的精确对位，让 goal 能正常判定结束。
            // 用 moveTo 而不是 setPos，保留朝向；位移极小，视觉上就是正常走到位。
            this.npc.getNavigation().stop();
            this.npc.moveTo(this.endPosX, this.npc.getY(), this.endPosZ,
                    this.npc.getYRot(), this.npc.getXRot());
            ci.cancel();
            return;
        }

        // 真的还差得远：重新寻路，不再因为重试次数多就瞬移。
        ++this.stuckCount;
        this.stuckTicks = 10;
        if (this.totalTicks > 30 && this.wasAttacked && this.isTooFar()) {
            // 这一条是原版为「被打跑太远回不来」留的兜底，保留。
            this.npc.setPos(this.endPosX, this.endPosY, this.endPosZ);
            this.npc.getNavigation().stop();
        } else {
            this.navigate(this.stuckCount % 2 == 1);
        }
        ci.cancel();
    }
}
