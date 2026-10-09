package bin.cnpcplus.perf.client;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * 把 {@link CnpcPlusConfig}（COMMON toml）的 1.20.1 临时优化配置项桥接到客户端渲染缓存。
 *
 * <p>不新建第二个配置文件：六个选项并入 {@code cnpcplus-common.toml}，
 * 由本类在配置加载/热重载时推送到 {@link RenderResourceCaches} 与 {@link ShadowRadiusLimit}
 * （语义等同 1.20.1 素材的 {@code bin.cnpcyouhua.perf.Config}）。
 *
 * <p>仅客户端注册（{@code value = Dist.CLIENT}）：服务端解析同一份 toml 时不会触碰任何
 * {@code perf.client} 类。推送不在渲染线程时，显存清理由
 * {@link RenderResourceCaches#requestReset()} 延迟到渲染线程执行。
 */
@EventBusSubscriber(modid = CnpcPlus.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class PerfConfigBridge {

    private PerfConfigBridge() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        if (isOurs(event)) {
            apply();
        }
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (isOurs(event)) {
            apply();
        }
    }

    private static boolean isOurs(ModConfigEvent event) {
        return event.getConfig().getSpec() == CnpcPlusConfig.SPEC;
    }

    private static void apply() {
        ShadowRadiusLimit.setMax(CnpcPlusConfig.SHADOW_RADIUS_MAX.get().floatValue());
        RenderResourceCaches.outlineCacheEnabled = CnpcPlusConfig.OUTLINE_CACHE_ENABLED.get();
        RenderResourceCaches.outlineCapacity = CnpcPlusConfig.OUTLINE_CACHE_ENTRIES.get();
        RenderResourceCaches.blueprintCacheEnabled = CnpcPlusConfig.BLUEPRINT_CACHE_ENABLED.get();
        RenderResourceCaches.blueprintBudgetNanos = (long) (CnpcPlusConfig.BLUEPRINT_BUDGET_MILLIS.get() * 1_000_000);
        RenderResourceCaches.blueprintMaxBlocks = CnpcPlusConfig.BLUEPRINT_MAX_BLOCKS_PER_FRAME.get();
        // 配置文件监听不保证在渲染线程，显存清理延迟到主线程。
        RenderResourceCaches.requestReset();
    }
}
