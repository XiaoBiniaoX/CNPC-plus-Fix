package bin.cnpcplus.fx;

import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** 服务端精确点集播放；每世界 32 实例 / 每 tick 1024 点，超预算轮转而非饿死后续形状。 */
public final class FxEngine {
    private static final Map<WorldServer, List<Instance>> QUEUES = new IdentityHashMap<>();

    private static final class Layer {
        final EnumParticleTypes particle;
        final FxShape shape;
        final double[] points;
        Layer(EnumParticleTypes particle, FxShape shape) {
            this.particle = particle;
            this.shape = shape;
            this.points = shape.points();
        }
    }

    private static final class Instance {
        /** 非 null 时粒子跟着这个实体走（投掷物跟随），null 表示固定在 x/y/z。 */
        final Entity owner;
        final double x, y, z;
        final long start;
        final List<Layer> layers = new ArrayList<>();
        int duration = 1, layer, point, totalPoints;
        Instance(Entity owner, double x, double y, double z, long now, List<FxConfig> configs) {
            this.owner = owner;
            this.x = x; this.y = y; this.z = z; this.start = now;
            for (FxConfig config : FxConfig.copy(configs)) {
                EnumParticleTypes type = FxConfig.resolve(config.particleId);
                if (type == null) continue;
                for (FxShape shape : config.shapes) {
                    Layer layer = new Layer(type, shape);
                    layers.add(layer);
                    duration = Math.max(duration, shape.duration());
                    totalPoints += layer.points.length / 2;
                }
            }
        }
        double px() { return owner != null && !owner.isDead ? owner.posX : x; }
        double py() { return owner != null && !owner.isDead ? owner.posY : y; }
        double pz() { return owner != null && !owner.isDead ? owner.posZ : z; }
    }

    public static void spawn(WorldServer world, double x, double y, double z, List<FxConfig> configs) {
        if (configs == null || configs.isEmpty()) return;
        List<Instance> queue = QUEUES.computeIfAbsent(world, ignored -> new ArrayList<>());
        if (queue.size() >= 32) return;
        Instance instance = new Instance(null, x, y, z, world.getTotalWorldTime(), configs);
        if (!instance.layers.isEmpty()) queue.add(instance);
    }

    /**
     * 投掷物跟随：粒子点集每 tick 以实体当前位置为原点重新发射。
     * 与 {@link #spawn} 的静态爆点共用同一套形状与预算，只是基准点跟着实体走。
     */
    public static void spawnFollow(WorldServer world, Entity owner, List<FxConfig> configs) {
        if (owner == null || owner.isDead || configs == null || configs.isEmpty()) return;
        List<Instance> queue = QUEUES.computeIfAbsent(world, ignored -> new ArrayList<>());
        if (queue.size() >= 32) return;
        Instance instance = new Instance(owner, owner.posX, owner.posY, owner.posZ, world.getTotalWorldTime(), configs);
        if (!instance.layers.isEmpty()) queue.add(instance);
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.world;
        List<Instance> queue = QUEUES.get(world);
        if (queue == null || queue.isEmpty()) return;
        int budget = 1024;
        for (Iterator<Instance> it = queue.iterator(); it.hasNext();) {
            Instance instance = it.next();
            // 跟随实例：投掷物落地/被移除后立刻停，不留一圈悬空粒子。
            if (instance.owner != null && (instance.owner.isDead || instance.owner.world != world)) { it.remove(); continue; }
            long age = world.getTotalWorldTime() - instance.start;
            if (age < 0 || age >= instance.duration) { it.remove(); continue; }
            if (budget > 0) budget -= drive(world, instance, age, Math.min(256, budget));
        }
        if (!queue.isEmpty()) Collections.rotate(queue, -1);
        else QUEUES.remove(world);
    }

    private static int drive(WorldServer world, Instance instance, long age, int budget) {
        int sent = 0, visited = 0;
        double baseX = instance.px(), baseY = instance.py(), baseZ = instance.pz();
        while (sent < budget && visited < instance.totalPoints) {
            Layer layer = instance.layers.get(instance.layer);
            int size = layer.points.length / 2;
            if (age < layer.shape.duration()) {
                double[] point = layer.shape.transform(layer.points[instance.point * 2], layer.points[instance.point * 2 + 1], age);
                world.spawnParticle(layer.particle, baseX + point[0], baseY + point[1], baseZ + point[2],
                        1, 0.0D, 0.0D, 0.0D, 0.0D, new int[0]);
                sent++;
                instance.point++;
                visited++;
            } else {
                visited += size;
                instance.point = size;
            }
            if (instance.point >= size) {
                instance.point = 0;
                instance.layer = (instance.layer + 1) % instance.layers.size();
            }
        }
        return sent;
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) QUEUES.remove(event.getWorld());
    }

    public static void clear() { QUEUES.clear(); }
}
