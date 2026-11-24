package bin.cnpcplus.script.tools;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IEntity;

public final class ScriptTeleport {
    public static final class SearchOptions {
        final int searchRadius, verticalRange, maxCandidates; final boolean requireSolidGround, requireNoCollision, requireNoLiquid, allowHazard;
        final double entityWidth, entityHeight;
        SearchOptions(int radius, int vertical, boolean ground, boolean collision, boolean liquid, boolean hazard, double width, double height, int max) {
            searchRadius = radius; verticalRange = vertical; requireSolidGround = ground; requireNoCollision = collision;
            requireNoLiquid = liquid; allowHazard = hazard; entityWidth = width; entityHeight = height; maxCandidates = max;
        }
        public int searchRadius() { return searchRadius; } public int verticalRange() { return verticalRange; } public int maxCandidates() { return maxCandidates; }
        public boolean requireSolidGround() { return requireSolidGround; } public boolean requireNoCollision() { return requireNoCollision; }
        public boolean requireNoLiquid() { return requireNoLiquid; } public boolean allowHazard() { return allowHazard; }
        public double entityWidth() { return entityWidth; } public double entityHeight() { return entityHeight; }
    }
    public static final class Options {
        final boolean preserveVelocity, preserveRotation, preserveVehicle, safeSearch;
        final SearchOptions searchOptions; final String dimensionChangePolicy;
        Options(boolean velocity, boolean rotation, boolean vehicle, boolean safe, SearchOptions search, String policy) {
            preserveVelocity = velocity; preserveRotation = rotation; preserveVehicle = vehicle; safeSearch = safe; searchOptions = search; dimensionChangePolicy = policy;
        }
        public boolean preserveVelocity() { return preserveVelocity; } public boolean preserveRotation() { return preserveRotation; }
        public boolean preserveVehicle() { return preserveVehicle; } public boolean safeSearch() { return safeSearch; }
        public SearchOptions searchOptions() { return searchOptions; } public String dimensionChangePolicy() { return dimensionChangePolicy; }
    }
    public static final class Result {
        private final boolean success; private final String status, reason; private final Position requestedPosition, actualPosition; private final int searchedCandidates;
        Result(boolean success, String status, String reason, Position requested, Position actual, int tested) {
            this.success = success; this.status = status; this.reason = reason; requestedPosition = requested; actualPosition = actual; searchedCandidates = tested;
        }
        public boolean success() { return success; } public String status() { return status; } public String reason() { return reason; }
        public Position requestedPosition() { return requestedPosition; } public Position actualPosition() { return actualPosition; }
        public int searchedCandidates() { return searchedCandidates; }
    }
    public SearchOptions searchOptions(int radius, int verticalRange, double width, double height, int maxCandidates) {
        return searchOptions(radius, verticalRange, true, true, true, false, width, height, maxCandidates);
    }
    public SearchOptions searchOptions(int radius, int verticalRange, boolean ground, boolean collision, boolean liquid,
            boolean allowHazard, double width, double height, int maxCandidates) {
        return new SearchOptions(radius, verticalRange, ground, collision, liquid, allowHazard, width, height, maxCandidates);
    }
    public Options options(boolean safeSearch) { return options(false, true, false, safeSearch, null, "DENY"); }
    public Options options(boolean preserveVelocity, boolean preserveRotation, boolean preserveVehicle, boolean safeSearch, SearchOptions search, String policy) {
        return new Options(preserveVelocity, preserveRotation, preserveVehicle, safeSearch, search, policy);
    }
    public Result findSafePosition(IWorld world, Position origin, SearchOptions options) { return search(ScriptWorld.level(world), origin, options, null, true); }
    public Result teleport(IEntity<?> wrapper, Position destination, Options options) {
        ScriptWorld.server(); Entity entity = wrapper.getMCEntity();
        if (!(entity.world instanceof WorldServer)) return fail(destination, "WORLD_UNAVAILABLE", 0);
        return move(entity, (WorldServer)entity.world, destination, options);
    }
    public Result teleportToWorld(IEntity<?> entity, IWorld world, Position destination, Options options) { return move(entity.getMCEntity(), ScriptWorld.level(world), destination, options); }
    private Result move(Entity entity, WorldServer world, Position destination, Options options) {
        if (entity.isDead || !entity.isEntityAlive()) return fail(destination, "ENTITY_INVALID", 0);
        // Legacy dimension transfer may load intermediate chunks and recreate entities. No safe contract yet.
        if (entity.world != world || entity.isBeingRidden() || options.preserveVehicle && entity.isRiding()) return fail(destination, "UNSUPPORTED", 0);
        SearchOptions supplied = options.searchOptions == null ? searchOptions(4, 3, 1, 1, 128) : options.searchOptions;
        SearchOptions actual = searchOptions(supplied.searchRadius, supplied.verticalRange, supplied.requireSolidGround, supplied.requireNoCollision,
                supplied.requireNoLiquid, supplied.allowHazard, entity.width, entity.height, supplied.maxCandidates);
        Result checked = search(world, destination, actual, entity, options.safeSearch);
        if (!checked.success) return checked;
        Position to = checked.actualPosition; float yaw = options.preserveRotation ? entity.rotationYaw : 0, pitch = options.preserveRotation ? entity.rotationPitch : 0;
        entity.dismountRidingEntity();
        if (entity instanceof EntityPlayerMP) ((EntityPlayerMP)entity).connection.setPlayerLocation(to.x(), to.y(), to.z(), yaw, pitch);
        else { entity.setLocationAndAngles(to.x(), to.y(), to.z(), yaw, pitch); entity.setPositionAndUpdate(to.x(), to.y(), to.z()); }
        if (!options.preserveVelocity) { entity.motionX = entity.motionY = entity.motionZ = 0; entity.velocityChanged = true; }
        entity.fallDistance = 0;
        return new Result(true, "SUCCESS", null, destination, ScriptWorld.position(entity.getPositionVector()), checked.searchedCandidates);
    }
    private Result search(WorldServer world, Position origin, SearchOptions options, Entity ignored, boolean search) {
        if (!ScriptWorld.valid(origin) || options.searchRadius < 0 || options.searchRadius > 32 || options.verticalRange < 0 || options.verticalRange > 32
                || options.maxCandidates < 1 || options.maxCandidates > 4096 || !(options.entityWidth > 0 && options.entityWidth <= 32 && options.entityHeight > 0 && options.entityHeight <= 32)) return fail(origin, "INVALID_TARGET", 0);
        ScriptWorld.PositionValidationOptions validation = new ScriptWorld.PositionValidationOptions(false, search && options.requireSolidGround,
                options.requireNoCollision, options.requireNoLiquid, options.entityWidth, options.entityHeight, options.requireNoCollision);
        String reason = ScriptWorld.validate(world, origin, validation, ignored);
        if (reason == null && (!search || options.allowHazard || !hazard(world, origin, options))) return new Result(true, "SUCCESS", null, origin, origin, 1);
        if (!search) return fail(origin, reason, 1);
        BlockPos center = ScriptWorld.block(origin); int tested = 1;
        // Increasing Manhattan distance, bounded by the same candidate budget as the modern API.
        for (int distance = 0; distance <= options.searchRadius * 2 + options.verticalRange; distance++) {
            for (int x = -options.searchRadius; x <= options.searchRadius; x++) for (int y = -options.verticalRange; y <= options.verticalRange; y++) {
                int z = distance - Math.abs(x) - Math.abs(y);
                if (z < 0 || z > options.searchRadius) continue;
                for (int sign = 1; sign >= -1; sign -= 2) {
                    if (z == 0 && sign < 0) continue;
                    Position point = new Position(center.getX() + x + .5, center.getY() + y, center.getZ() + sign * z + .5);
                    double dx = point.x() - origin.x(), dz = point.z() - origin.z();
                    if (dx * dx + dz * dz > options.searchRadius * options.searchRadius || point.equals(origin)) continue;
                    if (tested == options.maxCandidates) return fail(origin, "NO_SAFE_POSITION", tested);
                    tested++;
                    if (ScriptWorld.validate(world, point, validation, ignored) == null && (options.allowHazard || !hazard(world, point, options))) return new Result(true, "SUCCESS", null, origin, point, tested);
                }
            }
        }
        return fail(origin, "NO_SAFE_POSITION", tested);
    }
    private boolean hazard(WorldServer world, Position point, SearchOptions options) {
        double half = options.entityWidth / 2;
        BlockPos min = new BlockPos(point.x() - half, point.y() - .01, point.z() - half);
        BlockPos max = new BlockPos(point.x() + half, point.y() + options.entityHeight, point.z() + half);
        for (BlockPos pos : BlockPos.getAllInBoxMutable(min, max)) {
            Block block = world.getBlockState(pos).getBlock();
            if (block == Blocks.FIRE || block == Blocks.LAVA || block == Blocks.FLOWING_LAVA || block == Blocks.CACTUS || block == Blocks.MAGMA
                    || "cnpcplus:campfire".equals(String.valueOf(block.getRegistryName()))) return true;
        }
        return false;
    }
    private static Result fail(Position requested, String reason, int tested) { return new Result(false, reason, reason, requested, null, tested); }
}
