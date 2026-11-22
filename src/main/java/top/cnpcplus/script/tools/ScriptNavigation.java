package top.cnpcplus.script.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import noppes.npcs.api.entity.IEntity;

public final class ScriptNavigation {
    public record PathSnapshot(boolean isReachable, int nodeCount, double length, Position startPosition,
                               Position targetPosition, List<Position> nodes, String status, String failureReason) {}
    public record Options(int accuracy, int maxNodes) {}
    private record Probe(int tick, String outcome) {}
    private final WeakHashMap<Mob, Probe> probes = new WeakHashMap<>();

    public Options options(int accuracy, int maxNodes) {
        if (accuracy < 0 || accuracy > 8 || maxNodes < 1) throw new IllegalArgumentException("Invalid accuracy or node limit");
        return new Options(accuracy, maxNodes);
    }
    public OperationResult<PathSnapshot> canReach(IEntity<?> entity, Position target) { return canReach(entity, target, options(1, 256)); }
    public OperationResult<PathSnapshot> canReach(IEntity<?> entity, Position target, Options options) {
        int tick = ScriptWorld.server().getTickCount();
        if (!(entity.getMCEntity() instanceof Mob mob)) return OperationResult.failure("NAVIGATION", "ENTITY_NOT_MOB");
        if (!(mob.level() instanceof ServerLevel level) || mob.isRemoved()) return OperationResult.failure("NAVIGATION", "NAVIGATION_UNAVAILABLE");
        if (!ScriptWorld.valid(target)) return probe(mob, tick, "TARGET_INVALID");
        BlockPos pos = ScriptWorld.block(target);
        if (level.isOutsideBuildHeight(pos)) return probe(mob, tick, "TARGET_INVALID");
        if (!ScriptWorld.loaded(level, pos)) return probe(mob, tick, "TARGET_UNLOADED");
        Probe last = probes.get(mob);
        if (last != null && tick - last.tick < 10) return OperationResult.failure("NAVIGATION", "RATE_LIMITED");
        Path path = mob.getNavigation().createPath(pos, options.accuracy);
        if (path == null || !path.canReach()) return probe(mob, tick, "NO_PATH");
        if (path.getNodeCount() > options.maxNodes) return probe(mob, tick, "PATH_TOO_LONG");
        probes.put(mob, new Probe(tick, null));
        return OperationResult.success(snapshot(path, "REACHABLE"));
    }

    private OperationResult<PathSnapshot> probe(Mob mob, int tick, String reason) {
        probes.put(mob, new Probe(tick, reason));
        return OperationResult.failure("NAVIGATION", reason);
    }

    public String getState(IEntity<?> entity) {
        ScriptWorld.server();
        if (!(entity.getMCEntity() instanceof Mob mob) || mob.isRemoved()) return "UNKNOWN";
        var navigation = mob.getNavigation();
        Path path = navigation.getPath();
        if (path == null) return getFailureReason(entity) != null && "INTERRUPTED".equals(getFailureReason(entity)) ? "CANCELLED" : "IDLE";
        if (!navigation.isDone()) return "FOLLOWING";
        return path.canReach() && mob.blockPosition().distManhattan(path.getTarget()) <= 1 ? "REACHED" : "UNKNOWN";
    }

    public PathSnapshot getPath(IEntity<?> entity) {
        ScriptWorld.server();
        if (!(entity.getMCEntity() instanceof Mob mob)) return null;
        Path path = mob.getNavigation().getPath();
        return path == null ? null : snapshot(path, getState(entity));
    }

    public String getPathStatus(IEntity<?> entity) { return getState(entity); }

    public String getFailureReason(IEntity<?> entity) {
        ScriptWorld.server();
        if (!(entity.getMCEntity() instanceof Mob mob)) return "ENTITY_NOT_MOB";
        if (mob.isRemoved()) return "NAVIGATION_UNAVAILABLE";
        Probe probe = probes.get(mob);
        return probe == null ? null : probe.outcome;
    }

    public boolean stop(IEntity<?> entity) {
        int tick = ScriptWorld.server().getTickCount();
        if (!(entity.getMCEntity() instanceof Mob mob) || mob.isRemoved()) return false;
        mob.getNavigation().stop();
        probes.put(mob, new Probe(tick, "INTERRUPTED"));
        return true;
    }

    private PathSnapshot snapshot(Path path, String status) {
        var nodes = new ArrayList<Position>(path.getNodeCount());
        double length = 0;
        for (int i = 0; i < path.getNodeCount(); i++) {
            var node = path.getNode(i);
            Position pos = new Position(node.x, node.y, node.z);
            if (!nodes.isEmpty()) length += pos.distance(nodes.get(nodes.size() - 1));
            nodes.add(pos);
        }
        BlockPos target = path.getTarget();
        return new PathSnapshot(path.canReach(), nodes.size(), length, nodes.isEmpty() ? null : nodes.get(0),
                new Position(target.getX(), target.getY(), target.getZ()), List.copyOf(nodes), status, path.canReach() ? null : "NO_PATH");
    }
}
