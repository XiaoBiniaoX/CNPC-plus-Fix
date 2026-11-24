package bin.cnpcplus.script.tools;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;

public final class ScriptWorld {
    public static final class BlockSnapshot {
        private final String id;
        private final boolean air, solid, liquid, collision;
        public BlockSnapshot(String id, boolean air, boolean solid, boolean liquid, boolean collision) {
            this.id = id; this.air = air; this.solid = solid; this.liquid = liquid; this.collision = collision;
        }
        public String id() { return id; } public boolean air() { return air; }
        public boolean solid() { return solid; } public boolean liquid() { return liquid; } public boolean collision() { return collision; }
    }
    public static final class PositionValidationOptions {
        final boolean requireAir, requireSolidGround, requireNoCollision, requireNoLiquid, checkEntityCollision;
        final double entityWidth, entityHeight;
        public PositionValidationOptions(boolean air, boolean ground, boolean collision, boolean liquid, double width, double height, boolean entities) {
            requireAir = air; requireSolidGround = ground; requireNoCollision = collision; requireNoLiquid = liquid;
            entityWidth = width; entityHeight = height; checkEntityCollision = entities;
        }
        public boolean requireAir() { return requireAir; } public boolean requireSolidGround() { return requireSolidGround; }
        public boolean requireNoCollision() { return requireNoCollision; } public boolean requireNoLiquid() { return requireNoLiquid; }
        public boolean checkEntityCollision() { return checkEntityCollision; }
        public double entityWidth() { return entityWidth; } public double entityHeight() { return entityHeight; }
    }
    static MinecraftServer server() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || !server.isCallingFromMinecraftThread()) throw new IllegalStateException("CNPCPLUS.THREAD.MAIN_THREAD_ONLY");
        return server;
    }
    static WorldServer level(IWorld world) {
        MinecraftServer server = server(); WorldServer level = world.getMCWorld();
        if (level == null || server.getWorld(level.provider.getDimension()) != level) throw new IllegalStateException("CNPCPLUS.WORLD.UNAVAILABLE");
        return level;
    }
    static BlockPos block(Position pos) { return new BlockPos(pos.x(), pos.y(), pos.z()); }
    static Vec3d vec(Position pos) { return new Vec3d(pos.x(), pos.y(), pos.z()); }
    static Position position(Vec3d pos) { return new Position(pos.x, pos.y, pos.z); }
    static boolean valid(Position pos) {
        return pos != null && pos.isFinite() && Math.abs(pos.x()) < 30_000_000 && Math.abs(pos.z()) < 30_000_000 && Math.abs(pos.y()) < Integer.MAX_VALUE;
    }
    static boolean loaded(WorldServer world, int x, int z) { return world.getChunkProvider().getLoadedChunk(x, z) != null; }
    static boolean loaded(WorldServer world, BlockPos pos) { return loaded(world, pos.getX() >> 4, pos.getZ() >> 4); }
    static boolean loaded(WorldServer world, AxisAlignedBB box) {
        for (int x = (int)Math.floor(box.minX) >> 4; x <= (int)Math.floor(box.maxX) >> 4; x++)
            for (int z = (int)Math.floor(box.minZ) >> 4; z <= (int)Math.floor(box.maxZ) >> 4; z++) if (!loaded(world, x, z)) return false;
        return true;
    }
    public boolean isChunkLoaded(IWorld world, int chunkX, int chunkZ) { return loaded(level(world), chunkX, chunkZ); }
    public boolean isPositionLoaded(IWorld world, Position position) { return blockStatus(level(world), position) == null; }
    public boolean isWithinBuildHeight(IWorld world, Position position) { level(world); return valid(position) && position.y() >= 0 && position.y() < 256; }
    private static String blockStatus(WorldServer world, Position position) {
        if (!valid(position)) return "INVALID_POSITION";
        if (position.y() < 0 || position.y() >= 256) return "OUT_OF_HEIGHT";
        return loaded(world, block(position)) ? null : "NOT_LOADED";
    }
    public OperationResult<IBlock> getBlockIfLoaded(IWorld world, Position position) {
        WorldServer level = level(world); String reason = blockStatus(level, position);
        return reason == null ? OperationResult.success(NpcAPI.Instance().getIBlock(level, block(position))) : OperationResult.failure("WORLD", reason);
    }
    public OperationResult<BlockSnapshot> getBlockStateIfLoaded(IWorld world, Position position) {
        WorldServer level = level(world); String reason = blockStatus(level, position);
        if (reason != null) return OperationResult.failure("WORLD", reason);
        BlockPos pos = block(position); return OperationResult.success(snapshot(level, pos, level.getBlockState(pos)));
    }
    static BlockSnapshot snapshot(WorldServer world, BlockPos pos, IBlockState state) {
        return new BlockSnapshot(state.getBlock().getRegistryName().toString(), state.getBlock().isAir(state, world, pos),
                state.isFullCube(), state.getMaterial().isLiquid(), state.getCollisionBoundingBox(world, pos) != null);
    }
    private <T> OperationResult<T> read(IWorld world, Position pos, java.util.function.Function<BlockSnapshot, T> field) {
        OperationResult<BlockSnapshot> state = getBlockStateIfLoaded(world, pos);
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
    public OperationResult<Position> validatePosition(IWorld world, Position position, PositionValidationOptions options) {
        String reason = validate(level(world), position, options, null);
        return reason == null ? OperationResult.success(position) : OperationResult.failure("WORLD", reason);
    }
    static String validate(WorldServer world, Position position, PositionValidationOptions options, Entity ignored) {
        String status = blockStatus(world, position);
        if (status != null) return status;
        BlockPos pos = block(position); double half = options.entityWidth / 2;
        AxisAlignedBB box = new AxisAlignedBB(position.x() - half, position.y(), position.z() - half,
                position.x() + half, position.y() + options.entityHeight, position.z() + half);
        if (box.minY < 0 || box.maxY > 256 || options.requireSolidGround && pos.getY() == 0) return "OUT_OF_HEIGHT";
        if (box.minX < world.getWorldBorder().minX() || box.maxX > world.getWorldBorder().maxX()
                || box.minZ < world.getWorldBorder().minZ() || box.maxZ > world.getWorldBorder().maxZ()) return "INVALID_POSITION";
        if (!loaded(world, box.grow(1))) return "NOT_LOADED";
        if (options.requireAir && !world.isAirBlock(pos)) return "NOT_AIR";
        if (options.requireSolidGround && !world.getBlockState(pos.down()).isSideSolid(world, pos.down(), EnumFacing.UP)) return "NO_GROUND";
        if (options.requireNoLiquid && world.containsAnyLiquid(box)) return "LIQUID";
        if (options.requireNoCollision && !world.getCollisionBoxes(null, box).isEmpty()) return "COLLISION";
        if (options.checkEntityCollision && !world.checkNoEntityCollision(box, ignored)) return "COLLISION";
        return null;
    }
    public Position getPosition(IEntity<?> entity) { server(); return position(entity.getMCEntity().getPositionVector()); }
}
