package top.cnpcplus.script.tools;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;
import top.cnpcplus.script.tools.ScriptMath.BlockPosition;
import top.cnpcplus.script.tools.ScriptMath.Ray;

public final class ScriptRayTrace {
    public record Options(double maxDistance, boolean includeFluids, boolean includePassableBlocks, IEntity<?> ignoreEntity,
                          Predicate<IEntity<?>> entityFilter, Predicate<ScriptWorld.BlockSnapshot> blockFilter,
                          String collisionMode, boolean traceBlocks, boolean traceEntities) {}
    public record Result(boolean hit, String hitType, Position hitPosition, double hitDistance, double hitDistanceSquared,
                         String hitFace, BlockPosition blockPosition, IEntity<?> entity, ScriptWorld.BlockSnapshot blockState,
                         boolean isInside, String missReason) {}

    public Options options(double maxDistance) { return options(maxDistance, false, false, null, null, null, "COLLIDER", true, true); }

    public Options options(double maxDistance, boolean includeFluids, boolean includePassableBlocks, IEntity<?> ignoreEntity,
                           Predicate<IEntity<?>> entityFilter, Predicate<ScriptWorld.BlockSnapshot> blockFilter,
                           String collisionMode, boolean traceBlocks, boolean traceEntities) {
        return new Options(maxDistance, includeFluids, includePassableBlocks, ignoreEntity, entityFilter, blockFilter,
                collisionMode, traceBlocks, traceEntities);
    }

    public Result cast(IWorld world, Ray ray, Options options) { return cast(world, ray, options, options.traceBlocks, options.traceEntities); }
    public Result castBlocks(IWorld world, Ray ray, Options options) { return cast(world, ray, options, true, false); }
    public Result castEntities(IWorld world, Ray ray, Options options) { return cast(world, ray, options, false, true); }

    private Result cast(IWorld world, Ray ray, Options options, boolean blocks, boolean entities) {
        ServerLevel level = ScriptWorld.level(world);
        double max = options.maxDistance;
        if (!(max >= 0 && max <= 1024)) return miss(ray.origin(), 0, "INVALID_DISTANCE");
        Position direction = ray.direction().normalize();
        Position end = ray.origin().add(direction.scale(max));
        if (!ScriptWorld.valid(ray.origin()) || !ScriptWorld.valid(end) || direction.isZero()) return miss(ray.origin(), 0, "INVALID_RAY");
        ClipContext.Block mode = switch (options.collisionMode) {
            case "COLLIDER" -> options.includePassableBlocks ? ClipContext.Block.OUTLINE : ClipContext.Block.COLLIDER;
            case "OUTLINE" -> ClipContext.Block.OUTLINE;
            case "VISUAL" -> ClipContext.Block.VISUAL;
            default -> null;
        };
        if (mode == null) return miss(ray.origin(), 0, "INVALID_COLLISION_MODE");
        Vec3 start = ScriptWorld.vec(ray.origin()), finish = ScriptWorld.vec(end);
        Entity ignored = options.ignoreEntity == null ? null : options.ignoreEntity.getMCEntity();
        ClipContext clip = new ClipContext(start, finish, mode, options.includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, ignored);
        Result hit = BlockGetter.traverseBlocks(start, finish, clip, (context, mutablePos) -> {
            BlockPos pos = mutablePos.immutable();
            if (!ScriptWorld.loaded(level, pos)) {
                AABB cell = new AABB(pos).inflate(0.000001);
                Vec3 boundary = cell.contains(start) ? start : cell.clip(start, finish).orElse(start);
                return miss(ScriptWorld.position(boundary), start.distanceTo(boundary), "NOT_LOADED");
            }
            if (!blocks || level.isOutsideBuildHeight(pos)) return null;
            var state = level.getBlockState(pos);
            var fluid = state.getFluidState();
            if (state.isAir() && fluid.isEmpty()) return null;
            var snapshot = ScriptWorld.snapshot(level, pos, state);
            if (options.blockFilter != null && !options.blockFilter.test(snapshot)) return null;
            var blockHit = level.clipWithInteractionOverride(start, finish, pos, context.getBlockShape(state, level, pos), state);
            var fluidHit = context.getFluidShape(fluid, level, pos).clip(start, finish, pos);
            var closest = fluidHit == null || (blockHit != null && blockHit.getLocation().distanceToSqr(start) <= fluidHit.getLocation().distanceToSqr(start)) ? blockHit : fluidHit;
            if (closest == null) return null;
            double squared = closest.getLocation().distanceToSqr(start);
            return new Result(true, "BLOCK", ScriptWorld.position(closest.getLocation()), Math.sqrt(squared), squared,
                    closest.getDirection().getName(), new BlockPosition(pos.getX(), pos.getY(), pos.getZ()), null, snapshot, closest.isInside(), null);
        }, context -> miss(end, max, "NO_HIT"));
        if (!entities || (hit.hitDistance == 0 && !"NO_HIT".equals(hit.missReason))) return hit;
        double closest = hit.hitDistanceSquared;
        Entity winner = null;
        Vec3 winnerPosition = null;
        Vec3 scanEnd = ScriptWorld.vec(hit.hitPosition);
        for (Entity entity : level.getEntities(ignored, new AABB(start, scanEnd).inflate(1), Entity::isPickable)) {
            AABB bounds = entity.getBoundingBox().inflate(entity.getPickRadius());
            Vec3 point = bounds.contains(start) ? start : bounds.clip(start, finish).orElse(null);
            if (point == null) continue;
            double squared = point.distanceToSqr(start);
            if (squared > closest || (squared == closest && !"NO_HIT".equals(hit.missReason))) continue;
            if (winner != null && squared == closest && entity.getUUID().compareTo(winner.getUUID()) >= 0) continue;
            if (options.entityFilter != null && !options.entityFilter.test(NpcAPI.Instance().getIEntity(entity))) continue;
            winner = entity;
            winnerPosition = point;
            closest = squared;
        }
        return winner == null ? hit : new Result(true, "ENTITY", ScriptWorld.position(winnerPosition), Math.sqrt(closest), closest,
                null, null, NpcAPI.Instance().getIEntity(winner), null, closest == 0, null);
    }

    private static Result miss(Position position, double distance, String reason) {
        return new Result(false, "MISS", position, distance, distance * distance, null, null, null, null, false, reason);
    }
}
