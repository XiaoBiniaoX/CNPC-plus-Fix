package top.cnpcplus.drop.fx.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import top.cnpcplus.drop.fx.DropFxCaps;
import top.cnpcplus.drop.fx.DropFxConfig;
import top.cnpcplus.drop.fx.ShapePreset;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 弹射物粒子尾迹跟踪器（234 派发「尾迹类型则复用我们的粒子框架，
 * 将掉落物对象变更为投掷物而已」）。
 *
 * <h3>语义：周期钉地 burst 拖尾</h3>
 * 发射时登记（快照已由 {@code MixinEntityProjectileBuffs} 在
 * {@code getStatProperties} 处拷好），<b>立即在弹射物当前位置 spawn 第一组形状</b>；
 * 之后每 {@code cycleLen} tick（=配置里最长形状 duration）在弹射物<b>当时的位置</b>
 * 再钉一组——弹射物飞过留下一串图案 = 尾迹；命中/消亡（实体 removed）即停。
 * 形状本身留在世界坐标（与掉落物合成特效同语义），不跟随实体旋转/俯仰
 * （{@code ShapeType.sample} 只有 XZ 平面，见规划「开放项 4」）。
 *
 * <h3>为什么不改引擎</h3>
 * {@link DropFxEngine#spawn} 只要 ServerLevel+坐标+配置，天然可复用；
 * 本类只负责「什么时候、在哪个坐标、再叫一次 spawn」，引擎的实例上限、
 * 点预算、生命周期全部继续生效（同屏 32 实例/1024 点每 tick 自动节流）。
 * 因此独立 {@code @SubscribeEvent}，引擎类零改动。
 *
 * <h3>上限与清理</h3>
 * 同时跟踪 ≤ {@link #MAX_TRACKED}（防 50 张弓齐射把地图灌满，超限丢弃+debug）；
 * 实体 removed 即移除；ServerStopped 全清（与 DropFxEngine 同批语义，
 * 防跨档残留——233d-UI8b 的 static 实体集合教训）。
 */
public final class ProjectileFxTracker {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    /** 同时跟踪的弹射物上限。 */
    public static final int MAX_TRACKED = 16;

    private static final Map<Entity, Track> TRACKED = new HashMap<>();

    private ProjectileFxTracker() {
    }

    private static final class Track {
        final List<DropFxConfig> fxs;
        final long cycleLen;
        long lastSpawnTick;

        Track(List<DropFxConfig> fxs, long cycleLen, long lastSpawnTick) {
            this.fxs = fxs;
            this.cycleLen = cycleLen;
            this.lastSpawnTick = lastSpawnTick;
        }
    }

    /**
     * 发射时登记并立刻 spawn 第一组（mixin 在 getStatProperties RETURN 调用，
     * 传入的 fxs 已是快照深拷贝——发射后编辑 NPC 不影响在飞的弹射物，
     * 与原版 int 字段发射时拷贝语义一致）。
     */
    public static void track(Entity projectile, List<DropFxConfig> fxs) {
        if (projectile == null || fxs == null || fxs.isEmpty()) return;
        if (!(projectile.level() instanceof ServerLevel level)) return;
        synchronized (TRACKED) {
            if (TRACKED.size() >= MAX_TRACKED && !TRACKED.containsKey(projectile)) {
                LOGGER.debug("[drop-fx] 尾迹跟踪数达上限 {}，丢弃弹射物 {}",
                        MAX_TRACKED, projectile.getName().getString());
                return;
            }
            long now = level.getGameTime();
            TRACKED.put(projectile, new Track(
                    deepCopy(fxs), cycleOf(fxs), now));
        }
        DropFxEngine.spawn(level,
                projectile.getX(), projectile.getY(), projectile.getZ(), fxs);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel level)) return;
        synchronized (TRACKED) {
            if (TRACKED.isEmpty()) return; // O(1) 短路：无尾迹弹射物=零开销
            long now = level.getGameTime();
            Iterator<Map.Entry<Entity, Track>> it = TRACKED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Entity, Track> entry = it.next();
                Entity e = entry.getKey();
                if (e.isRemoved()) { // 命中 discard/超时消亡即停止尾迹
                    it.remove();
                    continue;
                }
                Track t = entry.getValue();
                if (now - t.lastSpawnTick < t.cycleLen) continue;
                if (!(e.level() instanceof ServerLevel el)) {
                    it.remove();
                    continue;
                }
                t.lastSpawnTick = now;
                DropFxEngine.spawn(el, e.getX(), e.getY(), e.getZ(), t.fxs);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (TRACKED) {
            TRACKED.clear();
        }
    }

    /** cycleLen = 各形状最长 duration（sanitize 后保证 1..1200，非空调用方保证）。 */
    private static long cycleOf(List<DropFxConfig> fxs) {
        long max = 1;
        for (DropFxConfig c : fxs) {
            for (ShapePreset p : c.shapes) {
                if (p.durationTicks > max) max = p.durationTicks;
            }
        }
        return max;
    }

    private static List<DropFxConfig> deepCopy(List<DropFxConfig> fxs) {
        List<DropFxConfig> out = new ArrayList<>(fxs.size());
        for (DropFxConfig c : fxs) out.add(c.copy());
        return out;
    }
}
