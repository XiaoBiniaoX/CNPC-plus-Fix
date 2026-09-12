package bin.cnpcplus.mixin.corpse;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
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
 * 修「NPC 重生时会原地向上跳一下」。
 *
 * <h3>根因：起点 Y 被算高了一格</h3>
 * {@code reset():1227-1229} 用 {@code getStartYPos()} 做 {@code moveTo} 传送，
 * 而 {@code tick():492-493} 每 10 tick 会执行
 * {@code startYPos = calculateStartYPos(ais.startPos()) + 1.0;}，
 * {@code getStartYPos():1450-1453} 又优先返回这个缓存值。
 * 于是重生传送的目标高度比真实地面高一格，NPC 被放到空中再自由落体落回 ——
 * 视觉上就是「原地跳一下」。
 *
 * <h3>与 1.12.2 的差异（已实证，不能照抄）</h3>
 * 1.12.2 那边是两处偏高叠加（约 +2.0）：除了同样的缓存 {@code +1.0}，
 * 它的 {@code calculateStartYPos} 还用了 {@code IBlockState.getBoundingBox}，
 * 而 1.12.2 的 {@code BlockAir} 只重写 {@code getCollisionBoundingBox}、
 * 没重写 {@code getBoundingBox}，导致空气也返回满方块 AABB，地面探测第一次迭代
 * 就在起点那格返回，永远找不到真地面。
 *
 * <p>1.21.1 <b>没有</b>这个问题：{@code AirBlock} 明确重写了
 * {@code getShape} 返回 {@code Shapes.empty()}（原版 AirBlock:32-35），
 * 而 {@code calculateStartYPos:1455-1471} 正是用 {@code state.getShape} +
 * {@code shape.isEmpty()} 判空后继续下探，探测逻辑是好的。
 * 所以这里只需要抵掉那个 {@code +1.0}，不需要重写整套地面探测。
 *
 * <h3>为什么只改 reset() 这一处</h3>
 * {@code getStartYPos()} 另有六处使用（{@code EntityAIReturn:53/59/141}、
 * {@code EntityAIWander:79/107}、{@code NPCAttackSelector:51}、
 * {@code JobBuilder:124}、{@code ItemNpcWand:80}、{@code ItemSoulstoneFilled:121}），
 * 多数是拿去做寻路目标或距离比较，偏高一格无害甚至有用。
 * 改公共方法会牵连整套 AI 行为，风险远大于收益 —— 与 1.12.2 的取舍一致。
 *
 * <h3>服务端安全</h3>
 * {@code reset()} 的传送分支本身有 {@code !isClientSide()} 门禁（:1227），
 * 所以只在服务端执行。本类注册在通用段，只用 {@code Level}/{@code BlockState}/
 * {@code VoxelShape}/{@code BlockPos} 这些两端共有的类，不引用任何客户端代码。
 * 方法体是纯只读方块查询，下探循环有 {@code getMinBuildHeight()} 硬下界。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceRespawnY {

    @Redirect(method = "reset",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;getStartYPos()D"),
            require = 1)
    private double cnpcplus$groundYForRespawn(EntityNPCInterface npc) {
        double ground = cnpcplus$findGroundY(npc);
        // 找不到地面（脚下全空）就退回原版值，别把 NPC 扔到世界底部。
        return Double.isNaN(ground) ? npc.getStartYPos() : ground;
    }

    /**
     * 从起点向下找第一个真有碰撞的方块，返回其顶面高度。
     *
     * <p>算法与原版 {@code calculateStartYPos:1455-1471} 完全一致（含游泳 NPC
     * 穿水继续下探的语义），唯一区别是不加那个 {@code +1.0}。
     *
     * @return 地面顶面 Y；找不到时返回 NaN
     */
    @Unique
    private static double cnpcplus$findGroundY(EntityNPCInterface npc) {
        if (npc == null || npc.ais == null) return Double.NaN;
        Level level = npc.level();
        if (level == null) return Double.NaN;

        BlockPos startPos = npc.ais.startPos();
        if (startPos == null) return Double.NaN;

        BlockPos pos = startPos;
        while (pos.getY() > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getShape((BlockGetter) level, pos);
            if (shape.isEmpty()) {
                pos = pos.below();
                continue;
            }
            AABB bb = shape.bounds().move(pos);
            // 照抄原版对游泳 NPC 的语义：水体不算落脚点，继续往下找真正的底。
            if (npc.ais.movementType != 2) return bb.maxY;
            if (startPos.getY() > pos.getY()) return bb.maxY;
            if (!state.is(Blocks.WATER)) return bb.maxY;
            pos = pos.below();
        }
        return Double.NaN;
    }
}
