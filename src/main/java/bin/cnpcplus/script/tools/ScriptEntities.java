package bin.cnpcplus.script.tools;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IEntity;

public final class ScriptEntities {
    public static final class EntitySnapshot {
        private final String uuid, typeId, name;
        private final Position position; private final boolean alive; private final List<String> tags;
        public EntitySnapshot(String uuid, String typeId, String name, Position position, boolean alive, List<String> tags) {
            this.uuid = uuid; this.typeId = typeId; this.name = name; this.position = position; this.alive = alive;
            this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        }
        public String uuid() { return uuid; } public String typeId() { return typeId; } public String name() { return name; }
        public Position position() { return position; } public boolean alive() { return alive; } public List<String> tags() { return tags; }
    }
    public Query from(IWorld world) { return new Query(ScriptWorld.level(world)); }
    public EntitySnapshot snapshot(IEntity<?> wrapper) {
        ScriptWorld.server(); Entity entity = wrapper.getMCEntity();
        return new EntitySnapshot(entity.getUniqueID().toString(), type(entity).toString(), entity.getName(),
                ScriptWorld.position(entity.getPositionVector()), entity.isEntityAlive(), new ArrayList<>(entity.getTags()));
    }
    private static ResourceLocation type(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        return id == null ? new ResourceLocation(entity instanceof net.minecraft.entity.player.EntityPlayer ? "minecraft:player" : "minecraft:unknown") : id;
    }
    public static final class Query {
        private final WorldServer world;
        private final List<Predicate<Entity>> filters = new ArrayList<>();
        private Position sort; private boolean descending; private int limit = Integer.MAX_VALUE;
        Query(WorldServer world) { this.world = world; }
        public Query within(Position center, double radius) { return radius(center, radius, false); }
        public Query withinHorizontal(Position center, double radius) { return radius(center, radius, true); }
        private Query radius(Position center, double radius, boolean horizontal) {
            if (!Double.isFinite(radius) || radius < 0 || !ScriptWorld.valid(center)) throw new IllegalArgumentException("Invalid center or radius");
            filters.add(entity -> {
                double dx = entity.posX - center.x(), dz = entity.posZ - center.z(), dy = horizontal ? 0 : entity.posY - center.y();
                return dx * dx + dy * dy + dz * dz <= radius * radius;
            }); return this;
        }
        public Query withinBox(Position min, Position max) {
            if (!ScriptWorld.valid(min) || !ScriptWorld.valid(max) || min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) throw new IllegalArgumentException("Invalid box bounds");
            filters.add(entity -> entity.posX >= min.x() && entity.posX <= max.x() && entity.posY >= min.y() && entity.posY <= max.y() && entity.posZ >= min.z() && entity.posZ <= max.z()); return this;
        }
        public Query whereType(String typeId) { ResourceLocation id = new ResourceLocation(typeId); filters.add(entity -> type(entity).equals(id)); return this; }
        public Query whereName(String name) { filters.add(entity -> entity.getName().equals(name)); return this; }
        public Query whereTag(String tag) { filters.add(entity -> entity.getTags().contains(tag)); return this; }
        public Query whereAlive(boolean alive) { filters.add(entity -> entity.isEntityAlive() == alive); return this; }
        public Query where(Predicate<IEntity<?>> predicate) { filters.add(entity -> predicate.test(NpcAPI.Instance().getIEntity(entity))); return this; }
        public Query excluding(IEntity<?> wrapper) { ScriptWorld.server(); Entity ignored = wrapper.getMCEntity(); filters.add(entity -> entity != ignored); return this; }
        public Query excludingTypes(String[] typeIds) { Set<ResourceLocation> ids = Arrays.stream(typeIds).map(ResourceLocation::new).collect(Collectors.toSet()); filters.add(entity -> !ids.contains(type(entity))); return this; }
        public Query sortByDistance(Position origin) { sort = origin; descending = false; return this; }
        public Query sortByDistanceDescending(Position origin) { sort = origin; descending = true; return this; }
        public Query limit(int count) { if (count < 0) throw new IllegalArgumentException("count must be nonnegative"); limit = count; return this; }
        public IEntity<?> findFirst() { return first(select(sort, descending, Math.min(1, limit))); }
        public IEntity<?> findNearest(Position origin) { return first(select(origin, false, Math.min(1, limit))); }
        public IEntity<?>[] findAll() { return select(sort, descending, limit).stream().map(NpcAPI.Instance()::getIEntity).toArray(IEntity<?>[]::new); }
        public int count() { return select(null, false, limit).size(); }
        public boolean exists() { return !select(null, false, Math.min(1, limit)).isEmpty(); }
        private IEntity<?> first(List<Entity> result) { return result.isEmpty() ? null : NpcAPI.Instance().getIEntity(result.get(0)); }
        private List<Entity> select(Position origin, boolean reverse, int count) {
            if (ScriptWorld.server().getWorld(world.provider.getDimension()) != world) throw new IllegalStateException("CNPCPLUS.WORLD.UNAVAILABLE");
            if (count == 0) return Collections.emptyList();
            List<Entity> result = new ArrayList<>();
            for (Entity entity : world.loadedEntityList) {
                if (!entity.isDead && filters.stream().allMatch(filter -> filter.test(entity))) result.add(entity);
                if (origin == null && result.size() == count) break;
            }
            if (origin != null) {
                Comparator<Entity> distance = Comparator.comparingDouble(entity -> entity.getDistanceSq(origin.x(), origin.y(), origin.z()));
                result.sort((reverse ? distance.reversed() : distance).thenComparing(Entity::getUniqueID));
            }
            return result.subList(0, Math.min(count, result.size()));
        }
    }
}
