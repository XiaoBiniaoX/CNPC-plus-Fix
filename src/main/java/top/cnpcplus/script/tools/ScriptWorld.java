package top.cnpcplus.script.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;

public final class ScriptWorld {
    public record BlockSnapshot(String id, boolean air, boolean solid, boolean liquid, boolean collision) {}
    public record PositionValidationOptions(boolean requireAir, boolean requireSolidGround, boolean requireNoCollision,
                                            boolean requireNoLiquid, double entityWidth, double entityHeight,
                                            boolean checkEntityCollision) {}

    static MinecraftServer server() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) throw new IllegalStateException("CNPCPLUS.THREAD.MAIN_THREAD_ONLY");
        return server;
    }

    static ServerLevel level(IWorld world) {
        MinecraftServer server = server();
        ServerLevel level = world.getMCLevel();
        if (server.getLevel(level.dimension()) != level) throw new IllegalStateException("CNPCPLUS.WORLD.UNAVAILABLE");
        return level;
    }

    static BlockPos block(Position position) { return BlockPos.containing(position.x(), position.y(), position.z()); }
    static Vec3 vec(Position position) { return new Vec3(position.x(), position.y(), position.z()); }
    static Position position(Vec3 vec) { return new Position(vec.x, vec.y, vec.z); }
    static boolean valid(Position pos) {
        return pos != null && pos.isFinite() && Math.abs(pos.x()) < 30_000_000 && Math.abs(pos.z()) < 30_000_000
                && Math.abs(pos.y()) < Integer.MAX_VALUE;
    }

    static boolean loaded(ServerLevel world, int chunkX, int chunkZ) {
        return world.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
    }

    static boolean loaded(ServerLevel level, BlockPos block) { return loaded(level, block.getX() >> 4, block.getZ() >> 4); }

    static boolean loaded(ServerLevel level, AABB box) {
        for (int x = ((int) Math.floor(box.minX)) >> 4; x <= ((int) Math.floor(box.maxX)) >> 4; x++) {
            for (int z = ((int) Math.floor(box.minZ)) >> 4; z <= ((int) Math.floor(box.maxZ)) >> 4; z++) {
                if (!loaded(level, x, z)) return false;
            }
        }
        return true;
    }

    public boolean isChunkLoaded(IWorld world, int chunkX, int chunkZ) { return loaded(level(world), chunkX, chunkZ); }
    public boolean isPositionLoaded(IWorld world, Position pos) { return blockStatus(level(world), pos) == null; }
    public boolean isWithinBuildHeight(IWorld world, Position pos) {
        ServerLevel level = level(world);
        return valid(pos) && !level.isOutsideBuildHeight(block(pos));
    }

    private static String blockStatus(ServerLevel level, Position position) {
        if (!valid(position)) return "INVALID_POSITION";
        BlockPos pos = block(position);
        if (level.isOutsideBuildHeight(pos)) return "OUT_OF_HEIGHT";
        return loaded(level, pos) ? null : "NOT_LOADED";
    }

    public OperationResult<IBlock> getBlockIfLoaded(IWorld world, Position position) {
        ServerLevel level = level(world);
        String reason = blockStatus(level, position);
        return reason == null ? OperationResult.success(NpcAPI.Instance().getIBlock(level, block(position))) : OperationResult.failure("WORLD", reason);
    }

    public OperationResult<BlockSnapshot> getBlockStateIfLoaded(IWorld world, Position position) {
        ServerLevel level = level(world);
        String reason = blockStatus(level, position);
        if (reason != null) return OperationResult.failure("WORLD", reason);
        BlockPos pos = block(position);
        return OperationResult.success(snapshot(level, pos, level.getBlockState(pos)));
    }

    static BlockSnapshot snapshot(ServerLevel level, BlockPos pos, BlockState state) {
        return new BlockSnapshot(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), state.isAir(),
                state.isSolidRender(level, pos), !state.getFluidState().isEmpty(), !state.getCollisionShape(level, pos).isEmpty());
    }

    private <T> OperationResult<T> read(IWorld world, Position pos, java.util.function.Function<BlockSnapshot, T> field) {
        var state = getBlockStateIfLoaded(world, pos);
        return state.isSuccess() ? OperationResult.success(field.apply(state.getValue())) : OperationResult.failure("WORLD", state.getReason());
    }

    public OperationResult<String> getBlockIdIfLoaded(IWorld world, Position pos) { return read(world, pos, BlockSnapshot::id); }
    public OperationResult<Boolean> isAirIfLoaded(IWorld world, Position pos) { return read(world, pos, BlockSnapshot::air); }
    public OperationResult<Boolean> isSolidIfLoaded(IWorld world, Position pos) { return read(world, pos, BlockSnapshot::solid); }
    public OperationResult<Boolean> isLiquidIfLoaded(IWorld world, Position pos) { return read(world, pos, BlockSnapshot::liquid); }
    public OperationResult<Boolean> hasCollisionIfLoaded(IWorld world, Position pos) { return read(world, pos, BlockSnapshot::collision); }

    public PositionValidationOptions options(boolean requireAir, boolean requireSolidGround, boolean requireNoCollision,
                                             boolean requireNoLiquid, double width, double height, boolean checkEntityCollision) {
        if (!(width > 0 && width <= 32 && height > 0 && height <= 32)) throw new IllegalArgumentException("Entity size must be in (0, 32]");
        return new PositionValidationOptions(requireAir, requireSolidGround, requireNoCollision, requireNoLiquid, width, height, checkEntityCollision);
    }

    public OperationResult<Position> validatePosition(IWorld world, Position pos, PositionValidationOptions options) {
        String reason = validate(level(world), pos, options, null);
        return reason == null ? OperationResult.success(pos) : OperationResult.failure("WORLD", reason);
    }

    static String validate(ServerLevel level, Position position, PositionValidationOptions options, Entity ignored) {
        String status = blockStatus(level, position);
        if (status != null) return status;
        BlockPos pos = block(position);
        double half = options.entityWidth / 2;
        AABB box = new AABB(position.x() - half, position.y(), position.z() - half,
                position.x() + half, position.y() + options.entityHeight, position.z() + half);
        if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                || (options.requireSolidGround && level.isOutsideBuildHeight(pos.below()))) return "OUT_OF_HEIGHT";
        if (!level.getWorldBorder().isWithinBounds(box)) return "INVALID_POSITION";
        if (!loaded(level, box.inflate(1))) return "NOT_LOADED";
        if (options.requireAir && !level.getBlockState(pos).isAir()) return "NOT_AIR";
        if (options.requireSolidGround && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) return "NO_GROUND";
        if (options.requireNoLiquid && level.containsAnyLiquid(box)) return "LIQUID";
        if (options.requireNoCollision && level.getBlockCollisions(ignored, box).iterator().hasNext()) return "COLLISION";
        if (options.checkEntityCollision && !level.getEntityCollisions(ignored, box).isEmpty()) return "COLLISION";
        return null;
    }

    public Position getPosition(IEntity<?> entity) {
        server();
        return position(entity.getMCEntity().position());
    }
}
