package bin.cnpcplus.common;

import net.minecraft.world.World;

/**
 * 守住全局 {@code World.MAX_ENTITY_RADIUS}，不让大体积 NPC 永久抬高它（性能问题 5）。
 *
 * <h3>背景</h3>
 * CNPC 在两处 {@code updateHitbox} 里做
 * {@code if (width/2 > MAX_ENTITY_RADIUS) MAX_ENTITY_RADIUS = width/2;}
 * （{@code EntityNPCInterface} offset 173-192、{@code EntityCustomNpc} offset 190-209），
 * **只增不减、无任何复位**。而这是 {@code World} 的 static 字段，
 * 被所有 mod 的 {@code getEntitiesWithinAABB} 当作向外 padding，
 * 抬高后全服每次实体查询都要多扫区块，直到进程退出。
 *
 * <h3>为什么不用 @Redirect 拦 PUTSTATIC</h3>
 * 试过，实机启动即崩：
 * <pre>
 * VerifyError: Bad type on operand stack
 *   Location: EntityNPCInterface.updateHitbox()V @193: swap
 *   Reason:   Type double_2nd is not assignable to category1 type
 * </pre>
 * 该字段是 **double**（category-2，占两个栈槽），而 Mixin 为静态字段 Redirect
 * 生成的桥接代码含 {@code swap}，JVM 的 {@code swap} 只能操作 category-1 类型。
 * 这是 Mixin 对 double/long 静态字段 Redirect 的**已知限制**。
 *
 * 所以改成「HEAD 记基线 → TAIL 还原」，赋值发生在我们自己的普通代码里，
 * 不经 Mixin 桥接，不触发这个限制。
 *
 * <h3>为什么放在 common 包而不是 mixin 包内</h3>
 * 两个混入类（{@code MixinEntityNPCGlobalRadius} 与
 * {@code MixinEntityCustomNpcGlobalRadius}）要共享同一份嵌套深度状态。
 * 跨 mixin 访问对方的字段会让 Mixin 转换器无法解析（findings 阶段 23 的崩溃教训），
 * 且 mixin 包内的非 mixin 类被目标类引用会触发
 * {@code IllegalClassLoadError}（阶段 22 教训）。故放在 {@code bin.cnpcplus.common}。
 *
 * <h3>嵌套深度的必要性</h3>
 * {@code EntityCustomNpc.updateHitbox} offset 95 会调 {@code super.updateHitbox()}，
 * 还会在 offset 98 对 passengers 递归。所以同一线程内本方法可能重入多层，
 * 必须只让**最外层**那次负责还原，否则内层提前还原后外层又被抬高。
 *
 * <h3>只在被抬高时还原</h3>
 * 还原目标是「进入最外层时的值」，且仅当当前值比它更大才写回。
 * 这样别的 mod 在别处设置的值不会被我们抹掉 —— 我们只收回 CNPC 自己抬的那部分。
 */
public final class GlobalRadiusGuard {

    /** [0] = 最外层进入时的基线值，[1] = 嵌套深度。 */
    private static final ThreadLocal<double[]> STATE = new ThreadLocal<double[]>();

    private GlobalRadiusGuard() {
    }

    public static void enter() {
        double[] s = STATE.get();
        if (s == null) {
            STATE.set(new double[]{World.MAX_ENTITY_RADIUS, 1.0D});
        } else {
            s[1] += 1.0D;
        }
    }

    public static void exit() {
        double[] s = STATE.get();
        if (s == null) return;
        s[1] -= 1.0D;
        if (s[1] > 0.0D) return;          // 还在嵌套里，交给最外层
        STATE.remove();
        if (World.MAX_ENTITY_RADIUS > s[0]) {
            World.MAX_ENTITY_RADIUS = s[0];
        }
    }
}
