package top.cnpcplus.perf;

import top.cnpcplus.CnpcPlus;
import top.cnpcplus.perf.client.RenderResourceCaches;
import top.cnpcplus.perf.client.ShadowRadiusLimit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 本地渲染设置；不控制 AI、任务、攻击、脚本或权限。 */
@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.DoubleValue SHADOW = B.comment(
            "NPC 阴影半径上限，单位：格。0 关闭投影，2 为旧优化默认值。",
            "默认 999 保持普通 CNPC 原阴影尺寸；主动调小才会改变大体型 NPC 阴影。",
            "只影响影子，不改变模型、碰撞箱、伤害或动画。")
            .defineInRange("shadowRadiusMax", 999.0, 0.0, 999.0);
    private static final ForgeConfigSpec.BooleanValue OUTLINE = B.comment(
            "共享 CNPC 原版生成的 2D 轮廓。关闭后走原版实例缓存。",
            "缺图与越界 UV 仍由原版处理，不因失败而隐藏模型；F3+T 重载时清理显存缓存。")
            .define("outlineCacheEnabled", true);
    private static final ForgeConfigSpec.IntValue CAPACITY = B.comment(
            "共享轮廓最多保留多少项（不是 NPC 数量）。达到上限淘汰最久未用项，",
            "被淘汰的轮廓下次需要时会重新生成，不会让模型缺失。")
            .defineInRange("outlineCacheEntries", 2048, 64, 16384);
    private static final ForgeConfigSpec.BooleanValue PREVIEW = B.comment(
            "缓存建筑方块蓝图预览。关闭可回到 CNPC 原版预览绘制，便于兼容排查。",
            "只影响预览，不影响实际建筑操作。")
            .define("blueprintCacheEnabled", true);
    private static final ForgeConfigSpec.DoubleValue MILLIS = B.comment(
            "每帧用于构建预览的软时间预算（毫秒）。默认 2；调小减轻瞬时卡顿，",
            "代价是完整预览出现得更慢。不会减少最终预览方块。",
            "单个模组模型计算或一次 GPU 上传无法中断，故不是绝对的帧时间上限。")
            .defineInRange("blueprintBudgetMillis", 2.0, 0.25, 20.0);
    private static final ForgeConfigSpec.IntValue BLOCKS = B.comment(
            "每帧最多处理的预览方块数，与时间预算同时生效；包含空气。",
            "未完成的数据留到下一帧，不截断，不调整实际建造速度。")
            .defineInRange("blueprintMaxBlocksPerFrame", 2048, 16, 8192);
    public static final ForgeConfigSpec SPEC = B.build();
    private Config() {}
    @SubscribeEvent public static void onLoad(ModConfigEvent.Loading e) {
        if (e.getConfig().getSpec() == SPEC) apply();
    }
    @SubscribeEvent public static void onReload(ModConfigEvent.Reloading e) {
        if (e.getConfig().getSpec() == SPEC) apply();
    }
    private static void apply() {
        ShadowRadiusLimit.setMax(SHADOW.get().floatValue());
        RenderResourceCaches.outlineCacheEnabled = OUTLINE.get();
        RenderResourceCaches.outlineCapacity = CAPACITY.get();
        RenderResourceCaches.blueprintCacheEnabled = PREVIEW.get();
        RenderResourceCaches.blueprintBudgetNanos = (long)(MILLIS.get() * 1_000_000);
        RenderResourceCaches.blueprintMaxBlocks = BLOCKS.get();
        // 配置文件监听不保证在渲染线程，显存清理延迟到主线程。
        RenderResourceCaches.requestReset();
    }
}
