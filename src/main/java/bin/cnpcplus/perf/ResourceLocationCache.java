package bin.cnpcplus.perf;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化 #7 / #9 的共用设施：{@code String -> ResourceLocation} 全局缓存池。
 *
 * <p><b>为什么需要</b>：{@code ResourceLocation.parse(String)}（1.21.1 起唯一的
 * 公开单串解析入口；1.20.1 是 {@code new ResourceLocation(String)}）不是廉价操作。
 * 解析会 {@code split(':')}、对命名空间和路径分别做逐字符合法性校验
 * （{@code isValidNamespace} / {@code isValidPath}），非法时还要构造异常。
 * CNPC 在若干每帧执行的渲染路径上重复用同一个字符串调它：
 * <ul>
 *   <li>{@code OverlayTexturedRectComponent.render}（每帧 × 每个 overlay 组件；
 *       1.21.1.20251230 已改用 {@code tryParse}，此条按 CNPC 版本而定）</li>
 *   <li>{@code LayerNpcElytra.render}（每帧 × 每个戴鞘翅且有自定义披风的 NPC）</li>
 * </ul>
 * 换成一次 {@code HashMap} 查找后，这些路径上的解析开销和对象分配都归零。
 *
 * <p><b>为什么用 ConcurrentHashMap 而不是普通 HashMap</b>：
 * 虽然主要调用方在渲染线程，但 {@code ResourceLocation} 缓存也可能被资源重载线程摸到。
 * 这里的写入是幂等的（同一 key 永远映射到等价的 {@code ResourceLocation}，而
 * {@code ResourceLocation} 本身不可变），所以并发不需要额外同步，只需要保证不撕裂。
 *
 * <p><b>为什么不怕无界增长</b>：key 来自玩家配置的纹理路径字符串（披风/头饰/overlay 纹理）。
 * 这是一个有限集合，量级在几十到几百，且生命周期与存档一致。
 * 真要防守，设一个上限：超过 {@link #MAX_ENTRIES} 就直接返回新建对象而不入缓存，
 * 这样即使有人用脚本无限生成随机纹理路径也不会 OOM。
 *
 * <p><b>非法路径的处理</b>：{@code ResourceLocation.parse} 抛异常时，原版 CNPC 也会抛
 * （只是被上层 try/catch 吞掉）。这里保持一致：不吞异常，让调用方自己的
 * try/catch 或 Mixin 的 fallback 处理，避免把「纹理配错了」变成「静默不显示」这种更难查的问题。
 */
public final class ResourceLocationCache {

    /** 缓存条目上限。超过之后不再增长，退化为每次新建（行为与原版一致，只是不再省）。 */
    private static final int MAX_ENTRIES = 4096;

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>(64);

    private ResourceLocationCache() {
    }

    /**
     * 取得 {@code raw} 对应的 {@code ResourceLocation}。
     *
     * @param raw 纹理路径字符串，可以是 {@code null} 或空串
     * @return 对应的 {@code ResourceLocation}；{@code raw} 为 {@code null} 或空串时返回 {@code null}
     * @throws net.minecraft.ResourceLocationException 当 {@code raw} 不是合法资源路径时
     *         （与原版 {@code ResourceLocation.parse(raw)} 行为一致）
     */
    public static ResourceLocation get(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        ResourceLocation cached = CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        // 先解析再决定是否入缓存：非法路径会在这里抛出，与原版时机相同。
        ResourceLocation created = ResourceLocation.parse(raw);
        if (CACHE.size() < MAX_ENTRIES) {
            CACHE.put(raw, created);
        }
        return created;
    }

    /**
     * {@link #get(String)} 的 {@code tryParse} 语义版本。
     *
     * <p>用于替换调用点本身就是 {@code ResourceLocation.tryParse(String)} 的场景
     * （如 1.21.1 的 {@code OverlayTexturedRectComponent.render}）：
     * 非法路径返回 {@code null} 而不是抛异常，与 {@code tryParse} 完全一致；
     * 成功的解析结果入缓存，下次直接命中。
     *
     * @param raw 纹理路径字符串，可以是 {@code null} 或空串
     * @return 对应的 {@code ResourceLocation}；{@code raw} 为 {@code null}/空串/非法路径时返回 {@code null}
     */
    public static ResourceLocation getOrNull(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        ResourceLocation cached = CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        ResourceLocation created = ResourceLocation.tryParse(raw);
        if (created != null && CACHE.size() < MAX_ENTRIES) {
            CACHE.put(raw, created);
        }
        return created;
    }
}
