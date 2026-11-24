package top.cnpcplus.script.tools;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IEntity;

public final class ScriptTeleport {
    public record SearchOptions(int searchRadius, int verticalRange, boolean requireSolidGround, boolean requireNoCollision,
                                boolean requireNoLiquid, boolean allowHazard, double entityWidth, double entityHeight, int maxCandidates) {}
    public record Options(boolean preserveVelocity, boolean preserveRotation, boolean preserveVehicle, boolean safeSearch,
                          SearchOptions searchOptions, String dimensionChangePolicy) {}
    public record Result(boolean success, String status, String reason, Position requestedPosition, Position actualPosition, int searchedCandidates) {}

    public SearchOptions searchOptions(int radius, int verticalRange, double width, double height, int maxCandidates) {
        return searchOptions(radius, verticalRange, true, true, true, false, width, height, maxCandidates);
    }
    public SearchOptions searchOptions(int radius, int verticalRange, boolean ground, boolean collision, boolean liquid,
                                       boolean allowHazard, double width, double height, int maxCandidates) {
        return new SearchOptions(radius, verticalRange, ground, collision, liquid, allowHazard, width, height, maxCandidates);
    }
    public Options options(boolean safeSearch) { return options(false, true, false, safeSearch, null, "DENY"); }
    public Options options(boolean preserveVelocity, boolean preserveRotation, boolean preserveVehicle, boolean safeSearch,
                           SearchOptions searchOptions, String dimensionChangePolicy) {
        return new Options(preserveVelocity, preserveRotation, preserveVehicle, safeSearch, searchOptions, dimensionChangePolicy);
    }
    public Result findSafePosition(IWorld world, Position origin, SearchOptions options) {
        return search(ScriptWorld.level(world), origin, options, null, true);
    }
    public Result teleport(IEntity<?> entity, Position destination, Options options) {
        ScriptWorld.server();
        Entity target = entity.getMCEntity();
        if (!(target.level() instanceof ServerLevel level)) return fail(destination, "WORLD_UNAVAILABLE", 0);
        return move(target, level, destination, options);
    }
    public Result teleportToWorld(IEntity<?> entity, IWorld world, Position destination, Options options) {
        ServerLevel level = ScriptWorld.level(world);
        return move(entity.getMCEntity(), level, destination, options);
    }

    private Result move(Entity entity, ServerLevel level, Position destination, Options options) {
        if (entity.isRemoved() || !entity.isAlive()) return fail(destination, "ENTITY_INVALID", 0);
        boolean changing = entity.level() != level;
        if ((changing && !"ALLOW".equals(options.dimensionChangePolicy))
                || (options.preserveVehicle && entity.isPassenger()) || entity.isVehicle()) return fail(destination, "UNSUPPORTED", 0);
        SearchOptions supplied = options.searchOptions == null ? searchOptions(4, 3, 1, 1, 128) : options.searchOptions;
        SearchOptions actual = new SearchOptions(supplied.searchRadius, supplied.verticalRange, supplied.requireSolidGround,
                supplied.requireNoCollision, supplied.requireNoLiquid, supplied.allowHazard, entity.getBbWidth(), entity.getBbHeight(), supplied.maxCandidates);
        Result checked = search(level, destination, actual, entity, options.safeSearch);
        if (!checked.success) return checked;
        Position to = checked.actualPosition;
        Vec3 velocity = entity.getDeltaMovement();
        float yaw = options.preserveRotation ? entity.getYRot() : 0;
        float pitch = options.preserveRotation ? entity.getXRot() : 0;
        if (!entity.teleportTo(level, to.x(), to.y(), to.z(), Set.of(), yaw, pitch)) {
            return fail(destination, changing ? "DIMENSION_CHANGE_FAILED" : "INVALID_TARGET", checked.searchedCandidates);
        }
        Entity arrived = changing ? level.getEntity(entity.getUUID()) : entity;
        if (arrived != null) {
            arrived.setDeltaMovement(options.preserveVelocity ? velocity : Vec3.ZERO);
            to = ScriptWorld.position(arrived.position());
        }
        return new Result(true, "SUCCESS", null, destination, to, checked.searchedCandidates);
    }

    private Result search(ServerLevel level, Position origin, SearchOptions options, Entity ignored, boolean search) {
        if (!ScriptWorld.valid(origin) || options.searchRadius < 0 || options.searchRadius > 32 || options.verticalRange < 0
                || options.verticalRange > 32 || options.maxCandidates < 1 || options.maxCandidates > 4096
                || !(options.entityWidth > 0 && options.entityWidth <= 32 && options.entityHeight > 0 && options.entityHeight <= 32)) {
            return fail(origin, "INVALID_TARGET", 0);
        }
        var validation = new ScriptWorld.PositionValidationOptions(false, search && options.requireSolidGround,
                options.requireNoCollision, options.requireNoLiquid, options.entityWidth, options.entityHeight, options.requireNoCollision);
        String status = ScriptWorld.validate(level, origin, validation, ignored);
        if (status == null && (!search || options.allowHazard || !hazard(level, origin, options))) return new Result(true, "SUCCESS", null, origin, origin, 1);
        if (!search) return fail(origin, status, 1);
        int tested = 1;
        for (BlockPos candidate : BlockPos.withinManhattan(ScriptWorld.block(origin), options.searchRadius, options.verticalRange, options.searchRadius)) {
            if (tested == options.maxCandidates) break;
            double dx = candidate.getX() + 0.5 - origin.x(), dz = candidate.getZ() + 0.5 - origin.z();
            if (dx * dx + dz * dz > options.searchRadius * options.searchRadius) continue;
            Position point = new Position(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
            if (point.equals(origin)) continue;
            tested++;
            if (ScriptWorld.validate(level, point, validation, ignored) == null && (options.allowHazard || !hazard(level, point, options))) {
                return new Result(true, "SUCCESS", null, origin, point, tested);
            }
        }
        return fail(origin, "NO_SAFE_POSITION", tested);
    }

    private boolean hazard(ServerLevel level, Position point, SearchOptions options) {
        double half = options.entityWidth / 2;
        BlockPos min = BlockPos.containing(point.x() - half, point.y() - 0.01, point.z() - half);
        BlockPos max = BlockPos.containing(point.x() + half, point.y() + options.entityHeight, point.z() + half);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE) || state.is(Blocks.LAVA) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW)
                    || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)) return true;
        }
        return false;
    }

    private static Result fail(Position origin, String reason, int tested) { return new Result(false, reason, reason, origin, null, tested); }
}
