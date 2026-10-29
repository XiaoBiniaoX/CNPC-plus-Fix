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
        sanitize(recipe.fxs, "配方 id=" + recipe.id);
    }

    /**
     * 列表版（234）：掉落物配方与弹射物特效（{@code RangedFxStore}）共用的裁剪主体，
     * {@code source} 只进日志（配方 id / NPC 来源标识）。
     *
     * <p>236 哈基彬拍板「每个粒子可设多个形状预设，但不应该重复设置同一个预设
     * 并且执行」→ 卫生顺序：①同 particleId 双 config 合并（防影子执行：GUI 只编辑
     * 首份而引擎全跑）→ ②每 config 内同 <b>type</b> 只留首个（与画廊查重同尺；
     * 233d-UI8 的 equals 全等去重被其包含，参数微差的同类型双份也清）→ ③上限
     * 截断 → ④空 config 清除。
     */
    public static void sanitize(List<DropFxConfig> fxs, String source) {
        // ① 同 particleId 合并（后见并入首见；防「看不见的第二份照样执行」）
        for (int i = fxs.size() - 1; i > 0; i--) {
            DropFxConfig c = fxs.get(i);
            if (c == null) { fxs.remove(i); continue; }
            for (int j = 0; j < i; j++) {
                DropFxConfig first = fxs.get(j);
                if (first != null && first.particleId.equals(c.particleId)) {
                    LOGGER.debug("[drop-fx] {} 合并重复粒子配置 {}（{} 个形状并入首份，236 拍板防影子执行）",
                            source, c.particleId, c.shapes.size());
                    first.shapes.addAll(c.shapes);
                    fxs.remove(i);
                    break;
                }
            }
        }
        if (fxs.size() > MAX_CONFIGS_PER_RECIPE) {
            LOGGER.debug("[drop-fx] {} 粒子配置数 {} 超上限，截断至 {}",
                    source, fxs.size(), MAX_CONFIGS_PER_RECIPE);
            while (fxs.size() > MAX_CONFIGS_PER_RECIPE) {
                fxs.remove(fxs.size() - 1);
            }
        }
        for (DropFxConfig c : fxs) {
            for (ShapePreset p : c.shapes) {
                p.withDefaults();
                if (p.durationTicks > MAX_DURATION_TICKS) {
                    LOGGER.debug("[drop-fx] {} 形状时长 {} 超上限，截断至 {}",
                            source, p.durationTicks, MAX_DURATION_TICKS);
                    p.durationTicks = MAX_DURATION_TICKS;
                }
            }
            // 同 type 去重：画廊本就「同类型只选中不添加」（233d-UI6/第七轮 15/15 根因修复），
            // 读入侧与之同尺——首见保留、后见丢弃，杜绝「重复设置同一个预设并且执行」。
            // 原 233d-UI8 equals 全等去重是本规则的子集（type 同必含参数同的重复）。
            // O(n²) n≤32，仅载入/回写时跑。
            for (int i = c.shapes.size() - 1; i > 0; i--) {
                ShapeType t = c.shapes.get(i).type;
                boolean dup = false;
                for (int j = 0; j < i; j++) {
                    if (c.shapes.get(j).type == t) {
                        dup = true;
                        break;
                    }
                }
                if (dup) {
                    LOGGER.debug("[drop-fx] {} 粒子 {} 去除同类型重复形状 {} (236 拍板，type 级)",
                            source, c.particleId, t.name());
                    c.shapes.remove(i);
                }
            }
            if (c.shapes.size() > MAX_SHAPES_PER_CONFIG) {
                LOGGER.debug("[drop-fx] {} 粒子 {} 形状数 {} 超上限，截断至 {}",
                        source, c.particleId, c.shapes.size(), MAX_SHAPES_PER_CONFIG);
                while (c.shapes.size() > MAX_SHAPES_PER_CONFIG) {
                    c.shapes.remove(c.shapes.size() - 1);
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
