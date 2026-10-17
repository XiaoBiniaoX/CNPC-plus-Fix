package top.cnpcplus.drop.fx;

import org.slf4j.Logger;
import top.cnpcplus.drop.DropRecipeData;

import java.util.ArrayList;
import java.util.List;

/**
 * 粒子框架的「物理保护层」上限（233d 用户拍板：无使用层上限表——这里全部是
 * 防炸服/防网络踢人的硬保护，正常使用永远摸不到；写在注释而不是界面里）。
 *
 * <ul>
 *   <li>配置侧：每配方粒子数/每粒子形状数——压 C→S PacketDropSave 的 NBT 体量
 *       （32767 字节硬上限，超了玩家直接被踢）；</li>
 *   <li>时长/点数：单次合成特效的时长与每 tick 点数，防长时间刷屏；</li>
 *   <li>运行时：每维度实例数与每 tick 全局点预算，防连续合成叠加卡服。</li>
 * </ul>
 * 从 .dat 或网络包进入的数据一律经 {@link #sanitize(DropRecipeData)} 裁剪，
 * 超限部分丢弃并 WARN（不崩、不拒绝整条配方）。
 */
public final class DropFxCaps {

    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    /** 每配方粒子配置数上限（网络 NBT 体量保护）。 */
    public static final int MAX_CONFIGS_PER_RECIPE = 16;
    /** 每粒子形状子配置数上限（同上）。 */
    public static final int MAX_SHAPES_PER_CONFIG = 32;
    /** 单形状时长上限（tick）≈ 60 秒。 */
    public static final int MAX_DURATION_TICKS = 1200;
    /**
     * 单形状采样点数上限（density 推导后 clamp）。
     * 233d-UI9 64→256：= POINT_BUDGET_PER_INSTANCE_TICK（单 tick 正好发完、不跨 tick 顺延）。
     * 原 64 会让 density≥4 即饱和——哈基彬反馈「半径放大后密度10仍太稀疏」；
     * 256 后 density 10→160 点可用，density≥16 饱和（tips 文案同步 1~256）。
     */
    public static final int MAX_POINTS_PER_SHAPE = 256;
    /** 每维度同时存在的特效实例上限（连续合成防御）。 */
    public static final int MAX_INSTANCES_PER_LEVEL = 32;
    /** 每实例每 tick 发送点预算（网络包防洪）。 */
    public static final int POINT_BUDGET_PER_INSTANCE_TICK = 256;
    /** 每维度每 tick 全局点预算（多实例叠加时的总闸）。 */
    public static final int POINT_BUDGET_PER_LEVEL_TICK = 1024;
    /** 服务端点集缓存条数（LRU，参数变即新键）。 */
    public static final int POINT_CACHE_MAX = 512;

    private DropFxCaps() {}

    /** 由 density 推导采样点数（引擎/GUI 预览共用同一公式）。 */
    public static int pointsFor(double density) {
        int n = (int) Math.round(density * 16);
        return Math.max(1, Math.min(MAX_POINTS_PER_SHAPE, n));
    }

    /**
     * 数据卫生裁剪：网络包/磁盘载入后调用。超限丢弃子项+WARN，保留合法部分。
     * （上限是物理保护而非使用限制——用户拍板「上限表无」，界面不展示这些数字。）
     */
    public static void sanitize(DropRecipeData recipe) {
        List<DropFxConfig> fxs = recipe.fxs;
        if (fxs.size() > MAX_CONFIGS_PER_RECIPE) {
            LOGGER.debug("[drop-fx] 配方 id={} 粒子配置数 {} 超上限，截断至 {}",
                    recipe.id, fxs.size(), MAX_CONFIGS_PER_RECIPE);
            while (fxs.size() > MAX_CONFIGS_PER_RECIPE) {
                fxs.remove(fxs.size() - 1);
            }
        }
        for (DropFxConfig c : fxs) {
            if (c.shapes.size() > MAX_SHAPES_PER_CONFIG) {
                LOGGER.debug("[drop-fx] 配方 id={} 粒子 {} 形状数 {} 超上限，截断至 {}",
                        recipe.id, c.particleId, c.shapes.size(), MAX_SHAPES_PER_CONFIG);
                while (c.shapes.size() > MAX_SHAPES_PER_CONFIG) {
                    c.shapes.remove(c.shapes.size() - 1);
                }
            }
            for (ShapePreset p : c.shapes) {
                p.withDefaults();
                if (p.durationTicks > MAX_DURATION_TICKS) {
                    LOGGER.debug("[drop-fx] 配方 id={} 形状时长 {} 超上限，截断至 {}",
                            recipe.id, p.durationTicks, MAX_DURATION_TICKS);
                    p.durationTicks = MAX_DURATION_TICKS;
                }
            }
            // 233d-UI8（用户拍板A）：同配置内全等形状去重——UI6 always-add 时代
            // 堆出的重复圆与用户新设的星形叠加播放=「设六芒星出来是圆形」头号嫌疑。
            // ShapePreset.equals=全字段值相等（与点集缓存同语义）；首见保留、后见丢弃。
            // O(n²) 但 n≤MAX_SHAPES_PER_CONFIG=32，仅在载入时跑一次。
            for (int i = c.shapes.size() - 1; i > 0; i--) {
                ShapePreset p = c.shapes.get(i);
                boolean dup = false;
                for (int j = 0; j < i; j++) {
                    if (c.shapes.get(j).equals(p)) {
                        dup = true;
                        break;
                    }
                }
                if (dup) {
                    LOGGER.debug("[drop-fx] 配方 id={} 粒子 {} 去除全等重复形状 {} (233d-UI8)",
                            recipe.id, c.particleId, p.type.name());
                    c.shapes.remove(i);
                }
            }
        }
        // 空粒子配置（无形状）无意义，直接清（GUI 删除最后一个形状时同步清 config 更干净，
        // 这里兜底磁盘/伪造包）
        for (int i = fxs.size() - 1; i >= 0; i--) {
            if (fxs.get(i).shapes.isEmpty()) fxs.remove(i);
        }
    }

    /** 空列表工厂（DropRecipeData 初始化用）。 */
    public static List<DropFxConfig> newList() {
        return new ArrayList<>();
    }
}
