package top.cnpcplus.perf;

import top.cnpcplus.CnpcPlus;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 优化 #4 的配套：世界卸载时清空 {@link MarketCache}。
 *
 * <h2>为什么必须清</h2>
 * {@link MarketCache} 的 key 是市场名（比如 {@code "shop1"}），
 * 而市场文件存在<b>存档目录</b>下（{@code CustomNpcs.getLevelSaveDirectory() + "/markets"}）。
 *
 * <p>单人模式下玩家可以「退出到主菜单 → 进另一个存档」而不重启游戏。
 * 如果不清缓存，第二个存档里同名的市场会读到第一个存档的库存 ——
 * 这是数据串档，比性能问题严重得多。
 *
 * <p>{@link MarketCache#dir()} 缓存的 {@code markets} 目录 {@code File} 对象
 * 同样必须失效，它指向的是上一个存档的路径。
 *
 * <h2>为什么用 {@code ServerStoppedEvent}</h2>
 * 这个事件在服务端（含单人模式的内置服务端）完全停止后触发，
 * 保证此时不会再有任何 {@code RoleTrader} 读写市场。
 * 用 {@code ServerStoppingEvent} 会早一点，但那时还可能有最后一批
 * {@code role.save} 在跑（{@code toSave} 落盘），清早了会让那次保存丢缓存一致性。
 */
@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID)
public final class MarketCacheLifecycle {

    private MarketCacheLifecycle() {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MarketCache.clear();
        // 顺带清掉优化 #2 的扫描状态：它持有 Level 与 BlockPos 的引用，
        // 跨存档残留会让下一个世界的第一次扫描指向已卸载的维度。
        MassBlockScanState.clear();
    }
}
