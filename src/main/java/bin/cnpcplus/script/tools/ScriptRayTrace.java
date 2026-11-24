package bin.cnpcplus.script.tools;

import java.util.function.Predicate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import noppes.npcs.api.*;
import noppes.npcs.api.entity.IEntity;

public final class ScriptRayTrace {
    public static final class Options {
        final double maxDistance; final boolean includeFluids, includePassableBlocks, traceBlocks, traceEntities;
        final IEntity<?> ignoreEntity; final Predicate<IEntity<?>> entityFilter;
        final Predicate<ScriptWorld.BlockSnapshot> blockFilter; final String collisionMode;
        Options(double max, boolean fluids, boolean passable, IEntity<?> ignore, Predicate<IEntity<?>> entities,
                Predicate<ScriptWorld.BlockSnapshot> blocks, String mode, boolean traceBlocks, boolean traceEntities) {
            maxDistance = max; includeFluids = fluids; includePassableBlocks = passable; ignoreEntity = ignore;
            entityFilter = entities; blockFilter = blocks; collisionMode = mode; this.traceBlocks = traceBlocks; this.traceEntities = traceEntities;
        }
        public double maxDistance() { return maxDistance; } public boolean includeFluids() { return includeFluids; }
        public boolean includePassableBlocks() { return includePassableBlocks; } public IEntity<?> ignoreEntity() { return ignoreEntity; }
        public Predicate<IEntity<?>> entityFilter() { return entityFilter; } public Predicate<ScriptWorld.BlockSnapshot> blockFilter() { return blockFilter; }
        public String collisionMode() { return collisionMode; } public boolean traceBlocks() { return traceBlocks; } public boolean traceEntities() { return traceEntities; }
    }
    public static final class Result {
        private final boolean hit, isInside; private final String hitType, hitFace, missReason;
        private final Position hitPosition; private final double hitDistance;
        private final ScriptMath.BlockPosition blockPosition; private final IEntity<?> entity; private final ScriptWorld.BlockSnapshot blockState;
        Result(boolean hit, String type, Position pos, double distance, String face, ScriptMath.BlockPosition block,
                IEntity<?> entity, ScriptWorld.BlockSnapshot state, boolean inside, String reason) {
            this.hit = hit; hitType = type; hitPosition = pos; hitDistance = distance; hitFace = face; blockPosition = block;
            this.entity = entity; blockState = state; isInside = inside; missReason = reason;
        }
        public boolean hit() { return hit; } public String hitType() { return hitType; } public Position hitPosition() { return hitPosition; }
        public double hitDistance() { return hitDistance; } public double hitDistanceSquared() { return hitDistance * hitDistance; }
        public String hitFace() { return hitFace; } public ScriptMath.BlockPosition blockPosition() { return blockPosition; }
        public IEntity<?> entity() { return entity; } public ScriptWorld.BlockSnapshot blockState() { return blockState; }
        public boolean isInside() { return isInside; } public String missReason() { return missReason; }
    }
    public Options options(double maxDistance) { return options(maxDistance, false, false, null, null, null, "COLLIDER", true, true); }
    public Options options(double maxDistance, boolean includeFluids, boolean includePassableBlocks, IEntity<?> ignoreEntity,
            Predicate<IEntity<?>> entityFilter, Predicate<ScriptWorld.BlockSnapshot> blockFilter, String collisionMode, boolean blocks, boolean entities) {
        return new Options(maxDistance, includeFluids, includePassableBlocks, ignoreEntity, entityFilter, blockFilter, collisionMode, blocks, entities);
    }
    public Result cast(IWorld world, ScriptMath.Ray ray, Options options) { return cast(world, ray, options, options.traceBlocks, options.traceEntities); }
    public Result castBlocks(IWorld world, ScriptMath.Ray ray, Options options) { return cast(world, ray, options, true, false); }
    public Result castEntities(IWorld world, ScriptMath.Ray ray, Options options) { return cast(world, ray, options, false, true); }
    private Result cast(IWorld wrapper, ScriptMath.Ray ray, Options options, boolean blocks, boolean entities) {
        WorldServer world = ScriptWorld.level(wrapper); double max = options.maxDistance;
        if (!(max >= 0 && max <= 1024)) return miss(ray.origin(), 0, "INVALID_DISTANCE");
        Position direction = ray.direction().normalize(), end = ray.origin().add(direction.scale(max));
        if (!ScriptWorld.valid(ray.origin()) || !ScriptWorld.valid(end) || direction.isZero()) return miss(ray.origin(), 0, "INVALID_RAY");
        if (!"COLLIDER".equals(options.collisionMode) && !"OUTLINE".equals(options.collisionMode)) return miss(ray.origin(), 0, "UNSUPPORTED_COLLISION_MODE");
        Vec3d start = ScriptWorld.vec(ray.origin()), finish = ScriptWorld.vec(end);
        Result hit = miss(end, max, "NO_HIT");
        int[] cell = {MathHelper.floor(start.x), MathHelper.floor(start.y), MathHelper.floor(start.z)};
        double[] origin = {start.x, start.y, start.z}, delta = {direction.x(), direction.y(), direction.z()}, next = new double[3];
        int[] step = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            step[axis] = delta[axis] > 0 ? 1 : delta[axis] < 0 ? -1 : 0;
            next[axis] = step[axis] == 0 ? Double.POSITIVE_INFINITY : (cell[axis] + (step[axis] > 0 ? 1 : 0) - origin[axis]) / delta[axis];
        }
        double entered = 0;
        while (entered <= max) {
            BlockPos pos = new BlockPos(cell[0], cell[1], cell[2]);
            // Neighbour-dependent block bounds must also stay within already loaded chunks.
            if (!ScriptWorld.loaded(world, new AxisAlignedBB(pos).grow(1))) {
                hit = miss(ray.origin().add(direction.scale(entered)), entered, "NOT_LOADED"); break;
            }
            if (blocks && cell[1] >= 0 && cell[1] < 256) {
                IBlockState state = world.getBlockState(pos);
                boolean liquid = state.getMaterial().isLiquid();
                AxisAlignedBB bounds = state.getCollisionBoundingBox(world, pos);
                if (!world.isAirBlock(pos) && (liquid ? options.includeFluids : bounds != null || options.includePassableBlocks || "OUTLINE".equals(options.collisionMode))) {
                    ScriptWorld.BlockSnapshot snapshot = ScriptWorld.snapshot(world, pos, state);
                    if (options.blockFilter == null || options.blockFilter.test(snapshot)) {
                        boolean outline = options.includePassableBlocks || "OUTLINE".equals(options.collisionMode) || liquid;
                        AxisAlignedBB selected = (outline ? state.getBoundingBox(world, pos) : bounds).offset(pos);
                        boolean inside = selected.contains(start);
                        RayTraceResult trace = outline ? selected.calculateIntercept(start, finish) : state.collisionRayTrace(world, pos, start, finish);
                        if (inside || trace != null) {
                            Vec3d point = inside ? start : trace.hitVec;
                            hit = new Result(true, "BLOCK", ScriptWorld.position(point), start.distanceTo(point), inside ? null : trace.sideHit.getName(),
                                    new ScriptMath.BlockPosition(cell[0], cell[1], cell[2]), null, snapshot, inside, null); break;
                        }
                    }
                }
            }
            int axis = next[0] <= next[1] && next[0] <= next[2] ? 0 : next[1] <= next[2] ? 1 : 2;
            entered = next[axis]; cell[axis] += step[axis]; next[axis] += Math.abs(1 / delta[axis]);
        }
        if (!entities) return hit;
        Entity ignored = options.ignoreEntity == null ? null : options.ignoreEntity.getMCEntity(), winner = null;
        Vec3d winnerPoint = null; double nearest = hit.hitDistanceSquared();
        for (Entity entity : world.loadedEntityList) {
            if (entity == ignored || entity.isDead || !entity.canBeCollidedWith()) continue;
            AxisAlignedBB box = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult trace = box.calculateIntercept(start, finish);
            Vec3d point = box.contains(start) ? start : trace == null ? null : trace.hitVec;
            if (point == null) continue;
            double squared = start.squareDistanceTo(point);
            if (squared > nearest || squared == nearest && !"NO_HIT".equals(hit.missReason)) continue;
            if (winner != null && squared == nearest && entity.getUniqueID().compareTo(winner.getUniqueID()) >= 0) continue;
            if (options.entityFilter != null && !options.entityFilter.test(NpcAPI.Instance().getIEntity(entity))) continue;
            winner = entity; winnerPoint = point; nearest = squared;
        }
        return winner == null ? hit : new Result(true, "ENTITY", ScriptWorld.position(winnerPoint), Math.sqrt(nearest), null, null,
                NpcAPI.Instance().getIEntity(winner), null, nearest == 0, null);
    }
    private static Result miss(Position point, double distance, String reason) { return new Result(false, "MISS", point, distance, null, null, null, null, false, reason); }
}
