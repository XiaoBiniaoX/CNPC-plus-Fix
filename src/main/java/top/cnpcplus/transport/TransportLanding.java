package top.cnpcplus.transport;

import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Resolves a stored transporter block position to a supported, collision-free player landing. */
public final class TransportLanding {
    private TransportLanding() {}

    public static Optional<Vec3> find(ServerPlayer player, net.minecraft.resources.ResourceKey<Level> dimension,
                                       BlockPos target) {
        if (player == null || player.getServer() == null || target == null) return Optional.empty();
        ServerLevel level = player.getServer().getLevel(dimension);
        return level == null ? Optional.empty() : find(player, level, target);
    }

    public static Optional<Vec3> find(ServerPlayer player, ServerLevel level, BlockPos target) {
        if (player == null || level == null || target == null) return Optional.empty();
        int x = target.getX();
        int z = target.getZ();
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        boolean exactSafe = safe(level, player, x, target.getY(), z);
        boolean surfaceSafe = safe(level, player, x, surfaceY, z);
        OptionalInt y = chooseY(target.getY(), surfaceY, exactSafe, surfaceSafe,
                level.getMinBuildHeight(), level.getMaxBuildHeight());
        return y.isPresent()
                ? Optional.of(new Vec3(x + 0.5, y.getAsInt(), z + 0.5))
                : Optional.empty();
    }

    static OptionalInt chooseY(int targetY, int surfaceY, boolean exactSafe, boolean surfaceSafe,
                               int minBuildHeight, int maxBuildHeight) {
        if (exactSafe && targetY >= minBuildHeight && targetY < maxBuildHeight) return OptionalInt.of(targetY);
        if (surfaceSafe && surfaceY >= minBuildHeight && surfaceY < maxBuildHeight) return OptionalInt.of(surfaceY);
        return OptionalInt.empty();
    }

    private static boolean safe(ServerLevel level, ServerPlayer player, int x, int y, int z) {
        if (y <= level.getMinBuildHeight() || y + player.getBbHeight() > level.getMaxBuildHeight()) return false;
        BlockPos feet = new BlockPos(x, y, z);
        if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) return false;

        double destX = x + 0.5;
        double destZ = z + 0.5;
        AABB box = player.getBoundingBox().move(destX - player.getX(), y - player.getY(), destZ - player.getZ());
        return level.noCollision(player, box);
    }
}
