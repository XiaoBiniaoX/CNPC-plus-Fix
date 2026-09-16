package bin.cnpcplus.mixin.perf;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 性能问题 7 与 8 的共同根因：用 {@code getBoundingBox} 判「有没有地面」，
 * 导致向下探测的循环体是死代码。
 *
 * <h3>根因（javap 字节码 + Forge jar 双向实证）</h3>
 * {@code calculateStartYPos(BlockPos)} 与 {@code calculateTopPos(BlockPos)}
 * 都想「从某点向下找第一个有碰撞的方块」，判据都是
 * {@code IBlockState.func_185900_c}（getBoundingBox）。但：
 * <ul>
 *   <li>{@code Block.getBoundingBox} 整个方法只有两条指令
 *       {@code 0: getstatic FULL_BLOCK_AABB / 3: areturn} —— **无条件返回满方块**；</li>
 *   <li>{@code BlockAir} 只重写 {@code getCollisionBoundingBox}（返回 {@code NULL_AABB}），
 *       **没有重写 {@code getBoundingBox}**；</li>
 *   <li>{@code StateImplementation.getBoundingBox} 只是直接转发给 {@code Block}；</li>
 *   <li>扫过 Forge jar 里 {@code net/minecraft/block/} 全部 285 个类，
 *       引用 {@code NULL_AABB} 的有 **0 个**。</li>
 * </ul>
 * 所以返回值恒非 null，{@code ifnull} 分支永远走不到，循环第一次迭代就返回。
 *
 * <h3>对哈基彬两条报告的修正</h3>
 * <ul>
 *   <li><b>问题 7</b>「每 10 tick 向下扫最多 256 层」：门禁确实是
 *       {@code field_70173_aa % 10}，但循环**实测平均只跑 1 次**，
 *       「256 层」只是理论上界。所以这不是性能瓶颈，而是**逻辑 bug**
 *       （{@code startYPos} 恒等于起点方块顶面 +1 而非真实地面）。</li>
 *   <li><b>问题 8</b>「重复检查同一方块」确实存在于 {@code calculateTopPos}
 *       （循环递减 {@code var2} 但三处坐标源 offset 13/23/29 全是 {@code aload_1}），
 *       但同因也只跑 1 次，且该方法**恒返回入参**。
 *       嵌套规模是 {@code tpTo} 的 3×3=9 次，不是 7×256。</li>
 * </ul>
 *
 * <h3>首版崩溃教训：不能直接把判据换成 getCollisionBoundingBox</h3>
 * 首版把 offset 30 的 {@code func_185900_c} Redirect 成
 * {@code func_185890_d}（getCollisionBoundingBox），实机进世界即崩：
 * <pre>
 * java.lang.NullPointerException: Ticking entity
 *   at EntityNPCInterface.calculateStartYPos(EntityNPCInterface.java:1382)
 *   at EntityNPCInterface.func_70071_h_(EntityNPCInterface.java:299)
 * </pre>
 * 原因看字节码的**执行顺序**就明白：
 * <pre>
 * 30: invokeinterface func_185900_c   ← 我换成了会返回 null 的版本
 * 36: invokevirtual  func_186670_a    ← 立刻对返回值调 .offset(pos) → NPE
 * 43: ifnull 94                       ← null 检查在解引用**之后**，永远来不及
 * </pre>
 * 原版能跑恰恰是因为 {@code getBoundingBox} 永不返回 null；
 * 我把它换成可能为 null 的版本，却没有接住 offset 36 那次解引用。
 *
 * <h3>正确修法：Redirect offset 36 的 offset() 调用</h3>
 * 保留原版 offset 30 的 {@code getBoundingBox}（不动，避免 null），
 * 改为拦 {@code AxisAlignedBB.func_186670_a}：
 * <ul>
 *   <li>自己用 {@code getCollisionBoundingBox} 重新判断这一格**是否真有碰撞**；</li>
 *   <li>没有碰撞（空气等）→ 返回 **null**，让 offset 43 的 {@code ifnull} 分支
 *       终于可达，循环继续向下探；</li>
 *   <li>有碰撞 → 返回真实的 {@code 碰撞箱.offset(pos)}，
 *       原版取其 {@code maxY} 作为地面高度。</li>
 * </ul>
 * 这样 null 只会出现在原版**已经准备好接住**的那个位置（局部变量 4，
 * 紧接着就是 {@code ifnull}），不会再有解引用。
 *
 * <h3>下探上界</h3>
 * 修好之后循环才真的会向下走，所以要给上界，否则悬空 NPC
 * （下方一路空气到 y=0）会真的扫 256 层。超过 32 层就返回一个满方块 AABB
 * 让循环终止，NPC 停在起点下方 32 格而非 y=0。
 *
 * <h3>与阶段 31 的 MixinEntityNPCInterfaceRespawnY 的关系（已核实）</h3>
 * 阶段 31 修「重生原地跳」时已 Redirect 掉 {@code reset()} 内那次
 * {@code getStartYPos()}，自行用 {@code getCollisionBoundingBox} 找地面 —— 同一根因。
 * 本混入修的是 {@code calculateStartYPos} **本体**，那个 Redirect 在功能上变冗余，
 * 但**刻意保留**：它还负责「找不到地面时退回原值」与「不把 NPC 扔到 y=0」的保底。
 * 两者共存无害，结果一致。
 *
 * <h3>服务端安全</h3>
 * 两个方法两端都调用。本混入注册 common 侧，只用 MC 方块/数学类型，无客户端引用。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCGroundProbe {

    /** 下探上界。超过这个深度就让循环终止，避免悬空 NPC 一路扫到 y=0。 */
    private static final int MAX_PROBE_DEPTH = 32;

    /**
     * {@code calculateStartYPos} 里那次 {@code AxisAlignedBB.offset(BlockPos)}。
     *
     * {@code func_186670_a}(offset) 与 {@code func_185890_d}(getCollisionBoundingBox)
     * 都是 **MC 成员**，CNPC 发布 jar 已 reobf，所以 {@code remap = false} 的
     * 注解 target 必须写 SRG 名；handler 体内直接调用则写 MCP 名由 reobf 转换
     * （findings「MC 类 vs noppes 类的命名方向」）。
     */
    @Redirect(method = "calculateStartYPos",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/AxisAlignedBB;func_186670_a(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/AxisAlignedBB;"),
            remap = false, require = 1)
    private AxisAlignedBB cnpcplus$groundOrNullForStart(AxisAlignedBB renderBox, BlockPos pos) {
        return this.cnpcplus$probe(renderBox, pos);
    }

    /**
     * {@code calculateTopPos} 里那次 {@code offset}，同上。
     *
     * 该方法还有「三处坐标都用入参 {@code var1}、只有返回值 {@code var2} 在递减」
     * 的原作者 bug（问题 8）。本修复不动它 —— 一旦 {@code ifnull} 分支可达，
     * 循环会在第一个有碰撞的方块处正常返回，重复查询不再发生。
     * 改坐标来源需要重写方法体，超出最小改动。
     */
    @Redirect(method = "calculateTopPos",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/math/AxisAlignedBB;func_186670_a(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/AxisAlignedBB;"),
            remap = false, require = 1)
    private AxisAlignedBB cnpcplus$groundOrNullForTop(AxisAlignedBB renderBox, BlockPos pos) {
        return this.cnpcplus$probe(renderBox, pos);
    }

    /**
     * 判断这一格是否真有落脚点。
     *
     * @return 有碰撞则返回 {@code 碰撞箱.offset(pos)}；没有则 **null**
     *         （交给原版紧随其后的 {@code ifnull} 分支继续向下探）
     */
    private AxisAlignedBB cnpcplus$probe(AxisAlignedBB renderBox, BlockPos pos) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (pos == null) {
            return renderBox == null ? null : renderBox.offset(pos);
        }
        // 超过下探上界：返回渲染箱（恒非 null）让循环立刻终止，
        // 语义上等于「就当这里是地面」，避免一路扫到 y=0。
        if (npc.posY - pos.getY() > MAX_PROBE_DEPTH) {
            return renderBox != null ? renderBox.offset(pos)
                    : net.minecraft.block.Block.FULL_BLOCK_AABB.offset(pos);
        }
        if (npc.world == null) {
            return renderBox == null ? null : renderBox.offset(pos);
        }
        IBlockState state = npc.world.getBlockState(pos);
        // getCollisionBoundingBox 对空气返回 null —— 这才是「有没有落脚点」的正确判据。
        AxisAlignedBB collision = state.getCollisionBoundingBox((IBlockAccess) npc.world, pos);
        if (collision == null) {
            return null;   // 原版 ifnull 分支终于可达，继续向下
        }
        return collision.offset(pos);
    }
}
