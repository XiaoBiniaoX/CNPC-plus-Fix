package top.cnpcplus.drop.fx.runtime;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
        spawn(level, cx, cy, cz, fxs, null);
    }

    public static void spawnFollowing(ServerLevel level, Entity projectile, List<DropFxConfig> fxs) {
        spawn(level, projectile.getX(), projectile.getY(), projectile.getZ(), fxs, projectile);
    }

    private static void spawn(ServerLevel level, double cx, double cy, double cz,
                               List<DropFxConfig> fxs, Entity anchor) {
        if (fxs == null || fxs.isEmpty()) return;
        List<DropFxInstance> queue = queueOf(level);
        if (queue.size() >= DropFxCaps.MAX_INSTANCES_PER_LEVEL) {
            LOGGER.debug("[drop-fx] 维度实例数达上限 {}，丢弃新特效 pos=({},{},{})",
                    DropFxCaps.MAX_INSTANCES_PER_LEVEL, cx, cy, cz);
            return;
        }
        DropFxInstance inst = new DropFxInstance(cx, cy, cz, fxs, level.getGameTime(), anchor);
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
            if (inst.anchor == null ? now - inst.startTick >= inst.maxDuration
                    : inst.anchor.isRemoved() || inst.anchor.level() != level) {
                it.remove();
                continue;
            }
            if (levelBudget <= 0) continue; // 即使预算耗尽也清理已消亡的跟随实例
            int consumed = drive(level, inst, now - inst.startTick,
                    Math.min(DropFxCaps.POINT_BUDGET_PER_INSTANCE_TICK, levelBudget));
            levelBudget -= consumed;
        }
    }

    /** 驱动单实例一个 tick 的发送，返回本 tick 消耗的点数。 */
    private static int drive(ServerLevel level, DropFxInstance inst, long age, int budget) {
        int consumed = 0;
        Vec3 center = inst.center();
        for (DropFxConfig cfg : inst.fxs) {
            if (budget <= 0) return consumed;
            ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(
                    new ResourceLocation(cfg.particleId));
            if (!(type instanceof ParticleOptions po)) continue; // 有数据型/未知 id：跳过
            for (ShapePreset shape : cfg.shapes) {
                if (budget <= 0) return consumed;
                long shapeAge = inst.shapeAge(age, shape);
                if (shapeAge >= shape.durationTicks) continue;
                double[] pts = DropFxSampler.points(shape);
                // 235 三轴旋转（360°，哈基彬拍板进框架核心）：
                // 相位 = rad(初始角) + rotSpeed*age（负速度自然反转），三轴串联顺序定为 X→Y→Z（欧拉定序）。
                // 绕 X 转 (y,z)、绕 Y 转 (x,z)、绕 Z 转 (x,y) 各为二维公式嵌入；采样点 y 恒 0，
                // 立起/翻滚的 y 位移由绕 X/Z 的旋转产生。三轴角全 0 时 cos=1/sin=0，
                // 逐点退化为旧 XZ 单轴公式（y'=0）= 老档行为恒等。
                double ax = Math.toRadians(shape.rotationX) + shape.rotSpeedX * shapeAge;
                double ay = Math.toRadians(shape.rotation) + shape.rotSpeed * shapeAge;
                double az = Math.toRadians(shape.rotationZ) + shape.rotSpeedZ * shapeAge;
                double cx = Math.cos(ax), sx = Math.sin(ax);
                double cy = Math.cos(ay), sy = Math.sin(ay);
                double cz = Math.cos(az), sz = Math.sin(az);
                int n = pts.length / 3;
                for (int i = 0; i < n; i++) {
                    if (budget <= 0) return consumed;
                    double px = pts[i * 3], py = pts[i * 3 + 1], pz = pts[i * 3 + 2];
                    // 绕 X：转 (y,z)
                    double y1 = py * cx - pz * sx;
                    double z1 = py * sx + pz * cx;
                    // 绕 Y：转 (x,z)
                    double x2 = px * cy - z1 * sy;
                    double z2 = px * sy + z1 * cy;
                    // 绕 Z：转 (x,y)
                    double x3 = x2 * cz - y1 * sz;
                    double y3 = x2 * sz + y1 * cz;
                    double x = center.x + shape.offsetX + x3;
                    double z = center.z + shape.offsetZ + z2;
                    double y = center.y + shape.offsetY + y3;
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
