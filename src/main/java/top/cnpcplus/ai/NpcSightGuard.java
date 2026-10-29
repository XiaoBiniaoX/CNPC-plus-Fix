package top.cnpcplus.ai;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** Validate NPC sight rays before vanilla's block traversal can load chunks. */
public final class NpcSightGuard {
    private NpcSightGuard() {}

    public static boolean isFinite(Vec3 point) {
        return Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }

    public static boolean canTrace(Vec3 from, Vec3 to, Predicate<BlockPos> isLoaded) {
        // NaN bypasses vanilla's distance > 128 check and can make traverseBlocks loop forever.
        if (!isFinite(from) || !isFinite(to) || from.distanceToSqr(to) > 128.0D * 128.0D) return false;
        // Use the same cells as vanilla, including its endpoint epsilon. No chunk generation
        // for AI sight: an unloaded cell is temporarily occluded, retried on the next scan.
        return BlockGetter.traverseBlocks(from, to, isLoaded,
                (loaded, pos) -> loaded.test(pos) ? null : Boolean.FALSE,
                loaded -> Boolean.TRUE);
    }
}
