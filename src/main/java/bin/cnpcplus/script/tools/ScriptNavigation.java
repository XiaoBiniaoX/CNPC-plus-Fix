package bin.cnpcplus.script.tools;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.pathfinding.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import noppes.npcs.api.entity.IEntity;

public final class ScriptNavigation {
    public static final class PathSnapshot {
        private final boolean reachable; private final double length; private final Position start, target;
        private final List<Position> nodes; private final String status, reason;
        PathSnapshot(boolean reachable, double length, Position start, Position target, List<Position> nodes, String status, String reason) {
            this.reachable = reachable; this.length = length; this.start = start; this.target = target;
            this.nodes = Collections.unmodifiableList(nodes); this.status = status; this.reason = reason;
        }
        public boolean isReachable() { return reachable; } public int nodeCount() { return nodes.size(); } public double length() { return length; }
        public Position startPosition() { return start; } public Position targetPosition() { return target; } public List<Position> nodes() { return nodes; }
        public String status() { return status; } public String failureReason() { return reason; }
    }
    public static final class Options {
        final int accuracy, maxNodes; Options(int accuracy, int maxNodes) { this.accuracy = accuracy; this.maxNodes = maxNodes; }
        public int accuracy() { return accuracy; } public int maxNodes() { return maxNodes; }
    }
    private static final class Probe { final int tick; final String reason; Probe(int tick, String reason) { this.tick = tick; this.reason = reason; } }
    private final Map<EntityLiving, Probe> probes = new WeakHashMap<>();
    public Options options(int accuracy, int maxNodes) {
        if (accuracy < 0 || accuracy > 8 || maxNodes < 1) throw new IllegalArgumentException("Invalid accuracy or node limit");
        return new Options(accuracy, maxNodes);
    }
    public OperationResult<PathSnapshot> canReach(IEntity<?> entity, Position target) { return canReach(entity, target, options(1, 256)); }
    public OperationResult<PathSnapshot> canReach(IEntity<?> entity, Position target, Options options) {
        int tick = ScriptWorld.server().getTickCounter();
        if (!(entity.getMCEntity() instanceof EntityLiving)) return OperationResult.failure("NAVIGATION", "ENTITY_NOT_MOB");
        EntityLiving mob = (EntityLiving)entity.getMCEntity();
        if (!(mob.world instanceof WorldServer) || mob.isDead) return OperationResult.failure("NAVIGATION", "NAVIGATION_UNAVAILABLE");
        WorldServer world = (WorldServer)mob.world;
        if (!ScriptWorld.valid(target) || target.y() < 0 || target.y() >= 256) return probe(mob, tick, "TARGET_INVALID");
        if (!ScriptWorld.loaded(world, ScriptWorld.block(target))) return probe(mob, tick, "TARGET_UNLOADED");
        Probe previous = probes.get(mob);
        if (previous != null && tick - previous.tick < 10) return OperationResult.failure("NAVIGATION", "RATE_LIMITED");
        int radius = (int)(mob.getNavigator().getPathSearchRange() + 8);
        if (!ScriptWorld.loaded(world, new AxisAlignedBB(mob.getPosition()).grow(radius))) return probe(mob, tick, "TARGET_UNLOADED");
        Path path = mob.getNavigator().getPathToPos(ScriptWorld.block(target));
        PathPoint last = path == null ? null : path.getFinalPathPoint();
        if (last == null || Math.abs(last.x - MathHelper.floor(target.x())) + Math.abs(last.y - MathHelper.floor(target.y()))
                + Math.abs(last.z - MathHelper.floor(target.z())) > options.accuracy) return probe(mob, tick, "NO_PATH");
        if (path.getCurrentPathLength() > options.maxNodes) return probe(mob, tick, "PATH_TOO_LONG");
        probes.put(mob, new Probe(tick, null)); return OperationResult.success(snapshot(path, "REACHABLE", target));
    }
    private OperationResult<PathSnapshot> probe(EntityLiving mob, int tick, String reason) { probes.put(mob, new Probe(tick, reason)); return OperationResult.failure("NAVIGATION", reason); }
    public String getState(IEntity<?> entity) {
        ScriptWorld.server();
        if (!(entity.getMCEntity() instanceof EntityLiving) || entity.getMCEntity().isDead) return "UNKNOWN";
        EntityLiving mob = (EntityLiving)entity.getMCEntity(); Path path = mob.getNavigator().getPath();
        if (path == null) return "INTERRUPTED".equals(getFailureReason(entity)) ? "CANCELLED" : "IDLE";
        return mob.getNavigator().noPath() ? "UNKNOWN" : "FOLLOWING";
    }
    public PathSnapshot getPath(IEntity<?> entity) {
        ScriptWorld.server();
        if (!(entity.getMCEntity() instanceof EntityLiving)) return null;
        Path path = ((EntityLiving)entity.getMCEntity()).getNavigator().getPath();
        return path == null ? null : snapshot(path, getState(entity), null);
    }
    public String getPathStatus(IEntity<?> entity) { return getState(entity); }
    public String getFailureReason(IEntity<?> entity) {
        ScriptWorld.server(); if (!(entity.getMCEntity() instanceof EntityLiving)) return "ENTITY_NOT_MOB";
        if (entity.getMCEntity().isDead) return "NAVIGATION_UNAVAILABLE";
        Probe probe = probes.get(entity.getMCEntity()); return probe == null ? null : probe.reason;
    }
    public boolean stop(IEntity<?> entity) {
        int tick = ScriptWorld.server().getTickCounter();
        if (!(entity.getMCEntity() instanceof EntityLiving) || entity.getMCEntity().isDead) return false;
        EntityLiving mob = (EntityLiving)entity.getMCEntity(); mob.getNavigator().clearPath(); probes.put(mob, new Probe(tick, "INTERRUPTED")); return true;
    }
    private PathSnapshot snapshot(Path path, String state, Position target) {
        List<Position> nodes = new ArrayList<>(); double length = 0;
        for (int i = 0; i < path.getCurrentPathLength(); i++) {
            PathPoint node = path.getPathPointFromIndex(i); Position position = new Position(node.x, node.y, node.z);
            if (!nodes.isEmpty()) length += position.distance(nodes.get(nodes.size() - 1)); nodes.add(position);
        }
        // 1.12.2 Path does not expose the requested target or a canReach flag for existing paths.
        return new PathSnapshot(target != null, length, nodes.isEmpty() ? null : nodes.get(0), target, nodes, state, target == null ? "UNKNOWN" : null);
    }
}
