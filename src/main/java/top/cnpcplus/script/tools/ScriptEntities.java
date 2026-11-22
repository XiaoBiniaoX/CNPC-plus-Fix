package top.cnpcplus.script.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

public final class ScriptEntities {
    public record EntitySnapshot(String uuid, String typeId, String name, Position position, boolean alive, List<String> tags) {}

    public Query from(IWorld world) { return new Query(ScriptWorld.level(world)); }

    public EntitySnapshot snapshot(IEntity<?> wrapper) {
        ScriptWorld.server();
        Entity entity = wrapper.getMCEntity();
        return new EntitySnapshot(entity.getUUID().toString(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.getName().getString(), ScriptWorld.position(entity.position()), entity.isAlive(), List.copyOf(entity.getTags()));
    }

    public static final class Query {
        private final ServerLevel world;
        private final List<Predicate<Entity>> filters = new ArrayList<>();
        private Position sort;
        private boolean descending;
        private int limit = Integer.MAX_VALUE;

        Query(ServerLevel world) { this.world = world; }

        public Query within(Position center, double radius) { return radius(center, radius, false); }
        public Query withinHorizontal(Position center, double radius) { return radius(center, radius, true); }
        private Query radius(Position center, double radius, boolean horizontal) {
            if (!Double.isFinite(radius) || radius < 0 || !ScriptWorld.valid(center)) throw new IllegalArgumentException("Invalid center or radius");
            filters.add(entity -> {
                double dx = entity.getX() - center.x(), dz = entity.getZ() - center.z();
                double dy = horizontal ? 0 : entity.getY() - center.y();
                return dx * dx + dy * dy + dz * dz <= radius * radius;
            });
            return this;
        }
        public Query withinBox(Position min, Position max) {
            if (!ScriptWorld.valid(min) || !ScriptWorld.valid(max) || min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
                throw new IllegalArgumentException("Invalid box bounds");
            }
            filters.add(entity -> entity.getX() >= min.x() && entity.getX() <= max.x()
                    && entity.getY() >= min.y() && entity.getY() <= max.y() && entity.getZ() >= min.z() && entity.getZ() <= max.z());
            return this;
        }
        public Query whereType(String typeId) {
            ResourceLocation id = new ResourceLocation(typeId);
            filters.add(entity -> BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).equals(id));
            return this;
        }
        public Query whereName(String name) { filters.add(entity -> entity.getName().getString().equals(name)); return this; }
        public Query whereTag(String tag) { filters.add(entity -> entity.getTags().contains(tag)); return this; }
        public Query whereAlive(boolean alive) { filters.add(entity -> entity.isAlive() == alive); return this; }
        public Query where(Predicate<IEntity<?>> predicate) { filters.add(entity -> predicate.test(NpcAPI.Instance().getIEntity(entity))); return this; }
        public Query excluding(IEntity<?> wrapper) { ScriptWorld.server(); Entity ignored = wrapper.getMCEntity(); filters.add(entity -> entity != ignored); return this; }
        public Query excludingTypes(String[] typeIds) {
            Set<ResourceLocation> ids = java.util.Arrays.stream(typeIds).map(ResourceLocation::new).collect(java.util.stream.Collectors.toSet());
            filters.add(entity -> !ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));
            return this;
        }
        public Query sortByDistance(Position origin) { sort = origin; descending = false; return this; }
        public Query sortByDistanceDescending(Position origin) { sort = origin; descending = true; return this; }
        public Query limit(int count) {
            if (count < 0) throw new IllegalArgumentException("count must be nonnegative");
            limit = count;
            return this;
        }

        public IEntity<?> findFirst() { return first(select(sort, descending, Math.min(1, limit))); }
        public IEntity<?> findNearest(Position origin) { return first(select(origin, false, Math.min(1, limit))); }
        public IEntity<?>[] findAll() {
            return select(sort, descending, limit).stream().map(NpcAPI.Instance()::getIEntity).toArray(IEntity<?>[]::new);
        }
        public int count() { return select(null, false, limit).size(); }
        public boolean exists() { return !select(null, false, Math.min(1, limit)).isEmpty(); }

        private IEntity<?> first(List<Entity> result) { return result.isEmpty() ? null : NpcAPI.Instance().getIEntity(result.get(0)); }

        private List<Entity> select(Position origin, boolean reverse, int count) {
            if (ScriptWorld.server().getLevel(world.dimension()) != world) throw new IllegalStateException("CNPCPLUS.WORLD.UNAVAILABLE");
            if (count == 0) return List.of();
            List<Entity> result = new ArrayList<>();
            for (Entity entity : world.getAllEntities()) {
                if (!entity.isRemoved() && filters.stream().allMatch(filter -> filter.test(entity))) result.add(entity);
                if (origin == null && result.size() == count) break;
            }
            if (origin != null) {
                Comparator<Entity> distance = Comparator.comparingDouble(entity -> entity.distanceToSqr(origin.x(), origin.y(), origin.z()));
                result.sort((reverse ? distance.reversed() : distance).thenComparing(Entity::getUUID));
            }
            return result.subList(0, Math.min(count, result.size()));
        }
    }
}
