package bin.cnpcplus.mixin.lifecycle;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修「重生时 NPC 原地 TP（原地跳一下）」（哈基彬需求）。
 *
 * <h3>症状</h3>
 * NPC 死亡重生（{@code reset()}）后会在原地向上蹦一下再落回来。哈基彬指出
 * 「主要是原地跳一下这个举动，有的时候会出问题」。
 *
 * <h3>根因：起点 Y 被算高了两格（全部字节码实证）</h3>
 * {@code reset()} 第 1235-1237 行用 {@code getStartYPos()} 做
 * {@code setLocationAndAngles} 传送，而这个 Y 值有两处叠加的偏高：
 *
 * <b>其一：{@code calculateStartYPos} 的地面探测根本不工作。</b>
 * 原版实现（反编译 1458-1473）是一个「从 pos 向下找第一个有碰撞箱的方块」的循环：
 * <pre>
 * AxisAlignedBB bb = state.getBoundingBox(world, pos).offset(pos);
 * if (bb != null) { ... return bb.maxY; }      // 找到地面
 * pos = pos.down();                            // 否则继续下探
 * </pre>
 * 问题在于它用的是 {@code IBlockState.getBoundingBox}，而不是
 * {@code getCollisionBoundingBox}。字节码实证：
 * <ul>
 *   <li>{@code Block.getBoundingBox} 的实现是无条件 {@code return FULL_BLOCK_AABB}
 *       （offset 0-3，整个方法就两条指令）；</li>
 *   <li>{@code BlockAir} 只重写了 {@code getCollisionBoundingBox}（返回
 *       {@code NULL_AABB}），**没有重写 {@code getBoundingBox}**，
 *       因此空气也返回满方块 AABB；</li>
 *   <li>{@code BlockStateContainer$StateImplementation.getBoundingBox} 只是
 *       直接转发给 {@code Block.getBoundingBox}，不做空气判断。</li>
 * </ul>
 * 于是循环**第一次迭代**就在起点那一格（通常是空气）拿到非 null 的
 * {@code FULL_BLOCK_AABB.offset(pos)}，立刻返回 {@code maxY = pos.getY() + 1.0}，
 * 永远不会向下找到真正的地面。那个 {@code if (bb != null)} 的 null 分支是死代码。
 *
 * <b>其二：缓存值又额外 +1.0。</b>
 * {@code func_70071_h_} 第 447 行每 10 tick 执行
 * {@code startYPos = calculateStartYPos(ais.startPos()) + 1.0;}，
 * 而 {@code getStartYPos()}（1453-1456）优先返回这个缓存值。
 *
 * 两者叠加：{@code getStartYPos()} ≈ {@code startPos.getY() + 2.0}。
 * NPC 被传送到高出地面两格的位置，然后自由落体落回 —— **这就是「原地跳一下」**。
 *
 * <h3>修法</h3>
 * 只 Redirect {@code reset()} 里那一次 {@code getStartYPos()} 调用，
 * 换成一个真正能找到地面的实现：用 {@code getCollisionBoundingBox}
 * （空气会正确返回 {@code NULL_AABB}）向下探，返回其 {@code maxY}。
 *
 * 几个刻意的取舍：
 * <ul>
 *   <li><b>只改 {@code reset()} 这一处，不改 {@code getStartYPos()} 本身。</b>
 *       后者还被 {@code EntityAIReturn}、{@code EntityAIWander}、
 *       {@code NPCAttackSelector}、{@code JobBuilder}、{@code ItemNpcWand}、
 *       {@code ItemSoulstoneFilled} 共六处使用，且它们多数是拿去做
 *       寻路目标或距离比较 —— 那些场景偏高一两格无害，甚至寻路时略高反而有用。
 *       动公共方法会牵连整套 AI 行为，风险远大于收益。</li>
 *   <li><b>保留 {@code movementType == 2}（游泳）的水下语义。</b>
 *       原版对游泳 NPC 会穿过水体继续下探（1464-1468 行），照抄该分支。</li>
 *   <li><b>找不到地面就退回原值。</b>下探到 y &lt;= 0 说明脚下全空，
 *       此时用原版值总比传送到 y=0 好。</li>
 * </ul>
 *
 * <h3>服务端安全</h3>
 * {@code reset()} 的传送分支本身就有 {@code !isRemote()} 门禁
 * （第 1235 行），所以这段只在服务端执行。本混入注册在 **common** 侧，
 * 不引用任何客户端类（只用 {@code World}、{@code IBlockState}、
 * {@code AxisAlignedBB}、{@code BlockPos} 这些两端共有的类），
 * 因此服务端加载安全。
 * 方法体内是纯只读的方块查询，不写世界、不生成方块、不发包。
 * 下探循环有 {@code y > 0} 的硬上界，不存在死循环。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceRespawnY {

    /**
     * 重生传送时改用真正的地面高度。
     *
     * target 指向的是 noppes 自己的方法（{@code getStartYPos}），没有 SRG 映射，
     * 所以写原名 —— 与 MC 成员必须写 SRG 名的规则相反，别混淆
     * （findings「MC 类 vs noppes 类的命名方向」）。
     */
    @Redirect(method = "reset",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;getStartYPos()D"),
            remap = false, require = 1)
    private double cnpcplus$groundYForRespawn(EntityNPCInterface npc) {
        double ground = this.cnpcplus$findGroundY(npc);
        // 找不到地面（脚下全是空的）就退回原版值，不要把 NPC 扔到 y=0。
        return ground < 0.0 ? npc.getStartYPos() : ground;
    }

    /**
     * 从起点向下找第一个**真有碰撞**的方块，返回其顶面高度。
     *
     * 与原版 {@code calculateStartYPos} 的唯一区别：用
     * {@code getCollisionBoundingBox} 而不是 {@code getBoundingBox}，
     * 这样空气才会正确地返回 {@code NULL_AABB} 让循环继续下探。
     *
     * @return 地面顶面 Y；找不到时返回 -1
     */
    @Unique
    private double cnpcplus$findGroundY(EntityNPCInterface npc) {
        if (npc == null || npc.ais == null) return -1.0;
        World world = npc.world;
        if (world == null) return -1.0;

        BlockPos startPos = npc.ais.startPos();
        if (startPos == null) return -1.0;

        BlockPos pos = startPos;
        while (pos.getY() > 0) {
            IBlockState state = world.getBlockState(pos);
            AxisAlignedBB bb = state.getCollisionBoundingBox(world, pos);
            if (bb != null) {
                AxisAlignedBB offset = bb.offset(pos);
                // 照抄原版对游泳 NPC 的语义（反编译 1464-1468）：
                // 水体不算落脚点，继续往下找真正的底。
                if (npc.ais.movementType == 2
                        && startPos.getY() <= pos.getY()
                        && state.getMaterial() == net.minecraft.block.material.Material.WATER) {
                    pos = pos.down();
                    continue;
                }
                return offset.maxY;
            }
            pos = pos.down();
        }
        return -1.0;
    }
}
