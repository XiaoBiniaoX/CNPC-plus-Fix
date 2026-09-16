package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 性能问题 4 与 10：两处大范围实体扫描 + 仇恨索敌的死逻辑。
 *
 * <h3>问题 4：NPC 受伤后的阵营求援扫描</h3>
 * {@code func_70097_a}（attackEntityFrom）字节码：
 * <pre>
 * 352-355  damage > 0 才进入
 * 368: ldc2_w 32.0d   ← X
 * 371: ldc2_w 16.0d   ← Y
 * 374: ldc2_w 32.0d   ← Z
 * 377: invokevirtual AxisAlignedBB.func_72314_b(DDD)
 * 380: invokevirtual World.func_72872_a(Class, AABB)
 * </pre>
 * {@code func_72314_b} 是**每轴双向**扩展，所以成盒约 64.6 × 33.9 × 64.6，
 * 跨 25-36 个区块。（哈基彬说的「64×32×64」体积正确，但源码常量是 32/16/32。）
 *
 * 触发条件不是周期性的：offset 280 要求 {@code isAttacking()} 为 **false**，
 * 即「**非战斗状态的 NPC 每挨一下打，就跑一次 64³ 全扫描**」。
 * 一群 NPC 被 AOE / 爆炸同帧命中时，N 次 64³ 扫描叠加 —— 这就是哈基彬说的
 * 「群殴、AOE、爆炸时形成连锁仇恨风暴」。
 *
 * 修法：范围 32/16/32 → 16/8/16（区块数 25→9，降约 64%）+ 每 NPC 冷却，
 * 两者都可 cfg 调。
 *
 * <h3>问题 10：faction getsAttacked 扫描</h3>
 * 在 {@code func_70636_d}（onLivingUpdate）offset 175-201，常量 16/16/16
 * （成盒约 32.6³，跨 9-16 区块），每 20 tick、仅服务端、仅非战斗、
 * 仅 {@code faction.getsAttacked} 开启时执行。
 *
 * 注意：这一项的实际开销**低于哈基彬的估计**。offset 229-232 的
 * {@code func_70638_az() == null} 廉价过滤在视线检测**之前**，
 * 所以只有「已有目标为空的 EntityMob」才会做 {@code canSee}，
 * 而 {@code canSee} 走的是 {@code EntitySenses}（vanilla 6 项 LRU 缓存），
 * 不是 {@code World.func_147447_a} 裸射线（该类内零个）。
 * 真瓶颈是 {@code func_72872_a} 的区块遍历本身，所以只降范围就够。
 *
 * <h3>为什么两项写在同一个混入</h3>
 * 目标同类（{@code EntityNPCInterface}）、手法同构（Redirect 掉 {@code func_72314_b}
 * 的参数）。分成两个混入类只会多一份样板，且两处都要读 cfg。
 *
 * <h3>命名注意</h3>
 * {@code func_72314_b}(grow) 与 {@code func_70068_e}(getDistanceSq) 都是 **MC 成员**，
 * CNPC 发布 jar 已 reobf，所以 {@code remap = false} 的注解 target 必须写 SRG 名
 * （findings「MC 类 vs noppes 类的命名方向」）。而 handler 方法体内直接调 MC 成员
 * 写 MCP 名，由 reobf 转换。
 *
 * <h3>服务端安全</h3>
 * 两处均在服务端路径（{@code func_70636_d} offset 51-58 有 {@code field_72995_K} 门禁；
 * {@code func_70097_a} 的伤害处理本身是服务端权威）。本混入注册 common 侧，
 * 只用 MC 实体与数学类型，无客户端引用。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCScanRange {

    /** 上次求援扫描的 tick。防 AOE 同帧连锁。 */
    @Unique private int cnpcplus$lastHurtScanTick = -1000;

    /**
     * 问题 4：受伤求援扫描降范围。
     *
     * Redirect {@code func_70097_a} 内那次 {@code func_72314_b}，把 32/16/32
     * 换成 cfg 值（默认 16/8/16）。Y 轴取水平范围的一半，与原版 32:16 的比例一致。
     */
    @Redirect(method = "func_70097_a",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/AxisAlignedBB;func_72314_b(DDD)Lnet/minecraft/util/math/AxisAlignedBB;"),
            remap = false, require = 1)
    private AxisAlignedBB cnpcplus$shrinkHurtScan(AxisAlignedBB box, double x, double y, double z) {
        double range = CnpcPlusConfig.getNpcHurtScanRange();
        return box.grow(range, range / 2.0D, range);
    }

    /**
     * 问题 4：给求援扫描加冷却，掐断 AOE 连锁。
     *
     * 注入在 {@code func_70097_a} 的 RETURN：此时原版扫描已经跑完，
     * 我们只负责记录时间戳。真正的拦截靠
     * {@link #cnpcplus$throttleHurtScan}（Redirect 掉扫描调用本身）。
     *
     * 为什么不在 HEAD 直接 cancel 整个方法：那会连伤害结算一起取消。
     */
    @Inject(method = "func_70097_a", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$markHurtScan(net.minecraft.util.DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        this.cnpcplus$lastHurtScanTick = npc.ticksExisted;
    }

    /**
     * 冷却判据：距上次扫描不足 cfg 间隔则跳过本次。
     *
     * 通过 Redirect {@code World.func_72872_a} 实现 —— 命中冷却时返回空表，
     * 后续 for 循环自然空转，不需要改变控制流。
     */
    @Redirect(method = "func_70097_a",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/World;func_72872_a(Ljava/lang/Class;Lnet/minecraft/util/math/AxisAlignedBB;)Ljava/util/List;"),
            remap = false, require = 1)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private java.util.List cnpcplus$throttleHurtScan(net.minecraft.world.World world,
                                                     Class type, AxisAlignedBB box) {
        int cooldown = CnpcPlusConfig.getNpcHurtScanCooldown();
        if (cooldown > 0) {
            EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
            if (npc.ticksExisted - this.cnpcplus$lastHurtScanTick < cooldown) {
                return java.util.Collections.emptyList();
            }
        }
        return world.getEntitiesWithinAABB(type, box);
    }

    /**
     * 问题 10：faction getsAttacked 扫描降范围。
     *
     * {@code func_70636_d} 内那次 {@code func_72314_b(16,16,16)} →
     * cfg 值（默认 12/8/12，区块数 9→4）。
     *
     * 注意 {@code func_70636_d} 内可能有多处 {@code func_72314_b}，
     * 用 {@code ordinal = 0} 锁定 getsAttacked 那一次（offset 195，
     * 是该方法内第一处，已由字节码确认）。
     */
    @Redirect(method = "func_70636_d",
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/util/math/AxisAlignedBB;func_72314_b(DDD)Lnet/minecraft/util/math/AxisAlignedBB;"),
            remap = false, require = 1)
    private AxisAlignedBB cnpcplus$shrinkGetsAttackedScan(AxisAlignedBB box,
                                                          double x, double y, double z) {
        double range = CnpcPlusConfig.getGetsAttackedScanRange();
        return box.grow(range, range * 2.0D / 3.0D, range);
    }
}
