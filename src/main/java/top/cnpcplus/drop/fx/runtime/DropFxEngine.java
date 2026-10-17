package top.cnpcplus.drop.fx.runtime;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import top.cnpcplus.drop.fx.DropFxCaps;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.ShapePreset;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 粒子特效服务端运行时（233d「安全异步」的主线程半边）。
 *
 * <p><b>驱动</b>：{@code LevelTickEvent} END 相，按维度队列——入口队列空 O(1) 短路
 * （同 232 DropCraftHandler PENDING 手法，无合成=零开销）。
 *
 * <p><b>发送</b>：{@code ServerLevel.sendParticles} 逐点精确发送（count=1、spread=0，
 * 保证形状不被 box 随机撒破坏）；每实例每 tick 点预算 {@link DropFxCaps#POINT_BUDGET_PER_INSTANCE_TICK}、
 * 每维度全局 {@link DropFxCaps#POINT_BUDGET_PER_LEVEL_TICK}——超预算顺延下一 tick，
 * 连续合成叠加也不会单 tick 包洪。
 *
 * <p><b>粒子类型解析</b>：{@code ForgeRegistries.PARTICLE_TYPES.getValue(id)} 拿到
 * {@code ParticleType<?>} 后 {@code instanceof ParticleOptions} 判定——
 * 只有零数据粒子类型（BasicParticleType/SimpleParticleType 及其子类）自身实现 ParticleOptions
 * 可直接发；Dust 等有数据型 type≠option，跳过并在 spawn 时 WARN（GUI 侧同判定置灰）。
 *
 * <p><b>生命周期</b>：spawn（合成时）→ 每 tick 发送 → age≥maxDuration 移除；
 * ServerStopped 清全部队列（维度对象随存档销毁，引用不跨档残留）。
 *
 * <p>客户端异步部分（GUI 预取点集）见 {@link DropFxSampler#prefetch}。
 */
public final class DropFxEngine {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    /** 每维度活跃特效队列（主线程读写；ServerStopped 清空）。IdentityHashMap：ServerLevel 恒定实例。 */
    private static final Map<ServerLevel, List<DropFxInstance>> LEVEL_QUEUES = new IdentityHashMap<>();

    private DropFxEngine() {}

    /** 合成触发（DropCraftHandler.execute 主线程调用）：建实例快照入队。 */
    public static void spawn(ServerLevel level, double cx, double cy, double cz,
                             List<DropFxConfig> fxs) {
        if (fxs == null || fxs.isEmpty()) return;
        List<DropFxInstance> queue = queueOf(level);
        if (queue.size() >= DropFxCaps.MAX_INSTANCES_PER_LEVEL) {
            LOGGER.debug("[drop-fx] 维度实例数达上限 {}，丢弃新特效 pos=({},{},{})",
                    DropFxCaps.MAX_INSTANCES_PER_LEVEL, cx, cy, cz);
            return;
        }
        DropFxInstance inst = new DropFxInstance(cx, cy, cz, fxs, level.getGameTime());
        queue.add(inst);
        // 233d-UI8：明细 particleId:[TYPE,…]——「出来是圆形」决定性日志：
        // 合成那一刻实际播放的形状类型一目可见（与保存日志对读；UI9 降为 debug 默认不输出）
        String fxsDetail = String.join(", ",
                inst.fxs.stream().map(fx -> fx.summary()).toList());
        LOGGER.debug("[drop-fx] 合成特效启动 configs={} pos=({},{},{}) dur={}t fxs=[{}]",
                inst.fxs.size(), cx, cy, cz, inst.maxDuration, fxsDetail);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        List<DropFxInstance> queue = queueOf(level);
        if (queue.isEmpty()) return; // O(1) 短路：绝大多数 tick 无特效

        int levelBudget = DropFxCaps.POINT_BUDGET_PER_LEVEL_TICK;
        long now = level.getGameTime();
        Iterator<DropFxInstance> it = queue.iterator();
        while (it.hasNext()) {
            DropFxInstance inst = it.next();
            if (now - inst.startTick >= inst.maxDuration) {
                it.remove();
                continue;
            }
            if (levelBudget <= 0) break; // 全局预算耗尽：本维度剩余实例顺延下 tick
            int consumed = drive(level, inst, now - inst.startTick,
                    Math.min(DropFxCaps.POINT_BUDGET_PER_INSTANCE_TICK, levelBudget));
            levelBudget -= consumed;
        }
    }

    /** 驱动单实例一个 tick 的发送，返回本 tick 消耗的点数。 */
    private static int drive(ServerLevel level, DropFxInstance inst, long age, int budget) {
        int consumed = 0;
        for (DropFxConfig cfg : inst.fxs) {
            if (budget <= 0) return consumed;
            ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(
                    new ResourceLocation(cfg.particleId));
            if (!(type instanceof ParticleOptions po)) continue; // 有数据型/未知 id：跳过
            for (ShapePreset shape : cfg.shapes) {
                if (budget <= 0) return consumed;
                if (age >= shape.durationTicks) continue;
                double[] pts = DropFxSampler.points(shape);
                // 运行时旋转：toRadians(rotation) + rotSpeed*age（负值自然反转）
                double angle = Math.toRadians(shape.rotation) + shape.rotSpeed * age;
                double cos = Math.cos(angle), sin = Math.sin(angle);
                int n = pts.length / 3;
                for (int i = 0; i < n; i++) {
                    if (budget <= 0) return consumed;
                    double px = pts[i * 3], pz = pts[i * 3 + 2];
                    double x = inst.cx + shape.offsetX + px * cos - pz * sin;
                    double z = inst.cz + shape.offsetZ + px * sin + pz * cos;
                    double y = inst.cy + shape.offsetY;
                    level.sendParticles(po, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
                    budget--;
                    consumed++;
                }
            }
        }
        return consumed;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEVEL_QUEUES.clear();
    }

    /** 取/建维度队列（IdentityHashMap：ServerLevel 同档内恒定实例）。 */
    private static List<DropFxInstance> queueOf(ServerLevel level) {
        return LEVEL_QUEUES.computeIfAbsent(level, k -> new ArrayList<>(8));
    }
}
