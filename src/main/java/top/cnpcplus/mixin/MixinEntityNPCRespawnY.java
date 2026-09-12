package top.cnpcplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修「NPC 重置/重生时原地往上跳一下」（哈基彬需求）。
 *
 * <h3>症状</h3>
 * NPC 死亡重生或被重置后会在原地向上蹦一下再落回来。
 *
 * <h3>根因：起点 Y 被人为 +1.0（反编译实证）</h3>
 * {@code reset()} 第 1319-1321 行用 {@code getStartYPos()} 做
 * {@code m_7678_}（moveTo）传送。而 {@code getStartYPos()}（{@code :1545-1548}）
 * 优先返回缓存字段 {@code startYPos}，那个字段在 {@code m_8119_} 第 513 行每 10 tick 刷新：
 * <pre>
 * this.startYPos = this.calculateStartYPos(this.ais.startPos()) + 1.0;
 * </pre>
 * 这个 {@code + 1.0} 就是全部原因：NPC 被传送到高出真实地面 1 格的位置，
 * 然后自由落体落回 —— 表现就是「原地跳一下」。
 *
 * <h3>与 1.12.2 的根因差异（重要，别照抄那边的修法）</h3>
 * 1.12.2 上还叠加了第二个缺陷：{@code calculateStartYPos} 的地面下探循环
 * 用 {@code IBlockState.getBoundingBox}，而空气**没有**重写该方法（只重写了
 * {@code getCollisionBoundingBox}），于是空气也返回满方块 AABB，
 * 循环第一次迭代就返回，永远找不到真地面，又额外偏高一格。
 *
 * <p>1.20.1 **没有**这个问题：{@code calculateStartYPos}（{@code :1550-1569}）
 * 改用了 {@code state.m_60808_(...)}（getShape）配 {@code m_83281_()}（isEmpty）判空气，
 * 空气的 VoxelShape 确实为空，下探会正确继续。所以这一版的地面探测本身是对的，
 * 唯一要抵消的就是那个 {@code + 1.0}。
 *
 * <h3>修法</h3>
 * 只 Redirect {@code reset()} 里那一次 {@code getStartYPos()}，返回自己算的真实地面高度
 * （与原版 {@code calculateStartYPos} 同一套逻辑，只是不加 1.0）。
 *
 * <p>刻意**不改** {@code getStartYPos()} 本身也不改 {@code m_8119_} 的 +1.0：
 * 那个方法还被 {@code EntityAIReturn}（endPosY / 寻路目标）、{@code EntityAIWander}、
 * {@code JobBuilder}、{@code ItemNpcWand} 等多处使用，多数是拿去做寻路目标或距离比较，
 * 偏高一格无害甚至有用（寻路目标略高于地面更容易被接受）。
 * 动公共方法会牵连整套 AI 行为，风险远大于收益。
 *
 * <h3>服务端安全</h3>
 * {@code reset()} 的传送分支本身带 {@code !isClientSide()} 门禁（{@code :1319}），
 * 所以这段实际只在服务端跑。本 mixin 注册在 common 侧，只引用两端共有的
 * {@code Level} / {@code BlockState} / {@code VoxelShape} / {@code BlockPos}，
 * 方法体是纯只读方块查询，不写世界、不发包。下探有 {@code getMinBuildHeight()} 硬下界，
 * 不存在死循环。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCRespawnY {

    /**
     * 重生传送时改用真实地面高度，抵消 m_8119_:513 的 +1.0。
     *
     * <p>target 指向 noppes 自己的方法，没有 SRG 映射，写原名。
     * （与 MC 成员必须写 SRG 名的规则相反，别混淆。）
     */
    @Redirect(method = "reset",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;getStartYPos()D"),
            remap = false, require = 1)
    private double cnpcplus$groundYForRespawn(EntityNPCInterface npc) {
        double ground = cnpcplus$findGroundY(npc);
        // 找不到地面（脚下全空）就退回原版值，不要把 NPC 扔到世界底部。
        return Double.isNaN(ground) ? npc.getStartYPos() : ground;
    }

    /**
     * 从起点向下找第一个真有碰撞的方块，返回其顶面高度。
     *
     * 与原版 {@code calculateStartYPos}（{@code :1550-1569}）逻辑一致，
     * 包括对游泳 NPC（{@code movementType == 2}）穿过水体继续下探的语义，
     * 唯一区别是不加那 1.0。
     *
     * @return 地面顶面 Y；找不到时返回 NaN
     */
    @Unique
    private double cnpcplus$findGroundY(EntityNPCInterface npc) {
        if (npc == null || npc.ais == null) return Double.NaN;
        Level level = npc.level();
        if (level == null) return Double.NaN;

        BlockPos startPos = npc.ais.startPos();
        if (startPos == null) return Double.NaN;
        // 原版 :1552 同样的守卫：区块没加载时不要去查方块。
        if (!level.hasChunkAt(startPos)) return Double.NaN;

        BlockPos pos = startPos;
        while (pos.getY() >= level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape(level, pos);
            if (shape.isEmpty()) {
                pos = pos.below();
                continue;
            }
            AABB bb = shape.bounds().move(pos);
            // 照抄原版对游泳 NPC 的语义（:1563-1566）：水体不算落脚点，继续往下找真正的底。
            if (npc.ais.movementType == 2
                    && startPos.getY() <= pos.getY()
                    && state.is(Blocks.WATER)) {
                pos = pos.below();
                continue;
            }
            return bb.maxY;
        }
        return Double.NaN;
    }
}
