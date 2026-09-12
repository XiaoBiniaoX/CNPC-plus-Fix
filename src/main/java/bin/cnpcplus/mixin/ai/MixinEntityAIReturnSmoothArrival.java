package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.pathfinding.PathNavigate;
import noppes.npcs.ai.EntityAIReturn;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修「返回起点时快到了却停顿一下再瞬移过去」（哈基彬需求 C-1）。
 *
 * <h3>症状与机制（字节码实证）</h3>
 * {@code EntityAIReturn.func_75246_d}（updateTask）的相关段：
 * <pre>
 *  10-17  totalTicks &gt; 600           → 20-36 func_70107_b(endPos)  43-46 clearPath  ← 瞬移点 1（超时）
 *  50-54  stuckTicks &gt; 0             → 57-67 --stuckTicks（这一刻什么都不做）
 *  70-80  navigator.noPath()          → 83-90 ++stuckCount; 93-96 stuckTicks = 10    ← 「停顿」10 tick
 *  99-120 totalTicks&gt;30 &amp;&amp; wasAttacked &amp;&amp; isTooFar()
 *  122-127 || stuckCount &gt; 5          → 130-146 func_70107_b(endPos) 149-156 clearPath ← 瞬移点 2（卡住）
 *  162-178 否则 navigate(stuckCount % 2 == 1)
 * </pre>
 * 关键在于「快到起点」这个时机会同时满足两个条件：
 * <ul>
 *   <li>{@code navigator.noPath()} 为真 —— 路径已经走完了；</li>
 *   <li>{@code isVeryNearAssignedPlace()} 仍为假 —— 它的阈值是
 *       {@code EntityNPCInterface.java:1445-1451} 里**硬编码的 ±0.2 格**，只判 x/z。
 *       而 {@code navigate()} 用 {@code tryMoveToXYZ(x, y, z, 1.0)}，
 *       寻路的到达判定精度远粗于 0.2 格，根本达不到。</li>
 * </ul>
 * 于是每次都被当成「卡住了」：先 {@code stuckTicks = 10} 冻结十刻（玩家看到的停顿），
 * 再重新寻路，几个来回后 {@code stuckCount &gt; 5} 成立 → setPosition 瞬移
 * （玩家看到的跳跃）。整个过程发生在离起点不到一格的地方，所以特别违和。
 *
 * <h3>修法：拦三处调用点，只在「已经很接近起点」时改变行为</h3>
 * <ol>
 *   <li><b>{@code func_70107_b}（setPosition）</b> —— 两个瞬移点共用同一个方法，
 *       一个 Redirect 全覆盖。近起点则不瞬移，并把 stuck 计数清零；
 *       真的很远（卡墙里、掉洞里、地形不可达）照原样瞬移，保底机制不拆。</li>
 *   <li><b>{@code func_75499_g}（clearPath）</b> —— 原版紧跟在每次瞬移之后。
 *       如果只跳过 setPosition 而让 clearPath 照常执行，NPC 会因为没有路径而
 *       干站在原地，等 600 tick 到了再被超时瞬移，症状只是换了个样子。
 *       所以抑制瞬移的那一刻要把 clearPath 换成「重新指向起点」。</li>
 *   <li><b>{@code func_75500_f}（noPath）</b> —— 这是「停顿」的源头判断。
 *       近起点且路径已走完时报告 false，就不会进入
 *       {@code ++stuckCount; stuckTicks = 10} 那一段，从根上消除停顿；
 *       同时就地补下一段路径，让它继续挪完最后这点距离。</li>
 * </ol>
 *
 * <h3>容差取值</h3>
 * 默认 3 格（cfg 可调，见 {@code CnpcPlusConfig.getReturnHomeArrivalTolerance}）。
 * 原版 0.2 格太严、寻路达不到；NPC 碰撞宽度 0.6、寻路节点整格对齐，
 * 3 格能覆盖「路径已到但差最后一两步」的全部情形，又不至于把真卡住的情况放过。
 *
 * <h3>为什么不直接改 isVeryNearAssignedPlace 的阈值</h3>
 * 它被 {@code func_75250_a}（shouldExecute）与 {@code func_75253_b}
 * （continueExecuting）共用。放宽阈值会让 NPC 更早认为「到了」而停止返回，
 * 结果停在离起点几格外不动 —— 那是另一个 bug。所以只动瞬移与卡住判定，
 * 不动到达判定。
 *
 * <h3>与既有骑乘混入的关系</h3>
 * {@code mixin/mount/MixinEntityAIReturnMount} 也混入本类，但它 Redirect 的是
 * {@code func_184218_aH()}（isRiding），位于 {@code func_75250_a} /
 * {@code func_75253_b}；本混入只碰 {@code func_75246_d} 里的三个调用点，
 * 互不重叠。
 *
 * <h3>命名注意</h3>
 * {@code func_70107_b}（setPosition）、{@code func_75499_g}（clearPath）、
 * {@code func_75500_f}（noPath）都是 **MC 的成员**，CNPC 发布 jar 已 reobf，
 * 所以在 {@code remap = false} 的混入里 target 必须写 SRG 名
 * （findings「MC 类 vs noppes 类的命名方向」，阶段 27 踩过一次）。
 * 而 handler 方法体内直接调用 MC 成员则写 MCP 名，由 reobf 转换（阶段 29 的反向坑）。
 */
@Mixin(value = EntityAIReturn.class, remap = false)
public abstract class MixinEntityAIReturnSmoothArrival {

    // 这些都是 EntityAIReturn 自己声明的 private 字段（非继承），
    // @Shadow 可以正常解析。findings 里「@Shadow 对 noppes 类不可靠」那条
    // 说的是**继承来的**成员，不适用于本类自有字段。
    @Shadow(remap = false) @Final private EntityNPCInterface npc;
    @Shadow(remap = false) private double endPosX;
    @Shadow(remap = false) private double endPosY;
    @Shadow(remap = false) private double endPosZ;
    @Shadow(remap = false) private int stuckTicks;
    @Shadow(remap = false) private int stuckCount;

    /** 本刻是否刚抑制了一次瞬移。用于把紧随其后的 clearPath 换成重新寻路。 */
    @Unique private boolean cnpcplus$suppressedTeleport;

    /**
     * 是否「已经足够接近起点，不该再瞬移」。
     *
     * 只比水平距离：竖直方向差异通常来自站在台阶/半砖上，不代表没走到。
     */
    @Unique
    private boolean cnpcplus$nearHome(EntityNPCInterface npc) {
        if (npc == null || !CnpcPlusConfig.isReturnHomeSmoothArrival()) return false;
        double tolerance = CnpcPlusConfig.getReturnHomeArrivalTolerance();
        double dx = npc.posX - this.endPosX;
        double dz = npc.posZ - this.endPosZ;
        return dx * dx + dz * dz <= tolerance * tolerance;
    }

    /** 重新把路径指向起点。 */
    @Unique
    private void cnpcplus$repath(EntityNPCInterface npc) {
        if (npc == null) return;
        PathNavigate nav = npc.getNavigator();
        if (nav == null) return;
        nav.tryMoveToXYZ(this.endPosX, this.endPosY, this.endPosZ, 1.0D);
    }

    /**
     * 两处瞬移。近起点则跳过，并清掉 stuck 计数避免下一刻又被判卡住。
     */
    @Redirect(method = "func_75246_d",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;func_70107_b(DDD)V"),
            remap = false, require = 1)
    private void cnpcplus$smoothArrival(EntityNPCInterface npc, double x, double y, double z) {
        if (!this.cnpcplus$nearHome(npc)) {
            this.cnpcplus$suppressedTeleport = false;
            if (npc != null) npc.setPosition(x, y, z);
            return;
        }
        // 抑制瞬移：清零两个 stuck 计数，让它按正常寻路走完最后这一段。
        this.cnpcplus$suppressedTeleport = true;
        this.stuckTicks = 0;
        this.stuckCount = 0;
    }

    /**
     * 紧跟瞬移之后的 clearPath。抑制过瞬移就换成重新寻路，否则照原样清路径。
     */
    @Redirect(method = "func_75246_d",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/pathfinding/PathNavigate;func_75499_g()V"),
            remap = false, require = 1)
    private void cnpcplus$keepWalking(PathNavigate nav) {
        if (this.cnpcplus$suppressedTeleport) {
            this.cnpcplus$suppressedTeleport = false;
            if (nav != null) {
                nav.tryMoveToXYZ(this.endPosX, this.endPosY, this.endPosZ, 1.0D);
            }
            return;
        }
        if (nav != null) nav.clearPath();
    }

    /**
     * 「停顿」的源头：noPath() 为真才会 {@code ++stuckCount; stuckTicks = 10}。
     *
     * 近起点时路径走完是**正常现象**而不是卡住，所以报告 false 并就地补一段路径。
     * 这样既不冻结十刻，也不会因为没路径而站住。
     */
    @Redirect(method = "func_75246_d",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/pathfinding/PathNavigate;func_75500_f()Z"),
            remap = false, require = 1)
    private boolean cnpcplus$notStuckNearHome(PathNavigate nav) {
        boolean noPath = nav != null && nav.noPath();
        if (!noPath) return false;
        if (!this.cnpcplus$nearHome(this.npc)) return true;
        this.cnpcplus$repath(this.npc);
        return false;
    }

    /**
     * 让超时瞬移的时间可以在 cfg 里改（哈基彬需求 C-1 的第二半）。
     *
     * 原版把 600 tick 硬编码在两处比较里：
     * {@code func_75246_d} offset 14（{@code totalTicks > 600} → 瞬移）与
     * {@code func_75253_b} offset 95 左右（{@code return totalTicks <= 600}）。
     * 两处都必须换成同一个值，否则会出现「AI 已经放弃执行但还没瞬移」
     * 或反之的错位。
     *
     * {@code @ModifyConstant} 一次覆盖同一方法内的全部匹配常量，
     * 这里用 method 数组同时作用于两个方法。
     *
     * 默认值与原版一致（30 秒 = 600 ticks），所以不改 cfg 就是原版行为。
     */
    @ModifyConstant(method = {"func_75246_d", "func_75253_b"},
            constant = @Constant(intValue = 600), remap = false, require = 1)
    private int cnpcplus$configurableTimeout(int original) {
        return CnpcPlusConfig.getReturnHomeTimeoutTicks();
    }
}
