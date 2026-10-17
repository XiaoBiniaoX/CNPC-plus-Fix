package top.cnpcplus.drop;

/**
 * 自定义掉落物配方界面布局，全部坐标硬编码（阶段232 从 AnvilLayout 复制改 2×2+箭头+结果）。
 * 放在 common 包，容器（服务端）也能安全引用。
 *
 * <p>槽位与 UI 不一致的教训（SmeltingLayout/SmithingLayout/AnvilLayout 注释链）：
 * 1. m_7856_ 会被二次调用，界面偏移一律用「居中基准+偏移」重算，禁止 += 累加；
 * 2. 槽背景框必须按容器真实 slotRenderX/Y 画（drawSlotBgAt），不写死；
 * 3. 开关/输入框坐标先盘周围控件（名称框、槽行、滚动列表 x172 起、按钮列 x306）再取空隙。
 *
 * <p>232 用户要求：无锤子装饰，2×2 紧凑输入 + 箭头 + 结果：
 * - 2×2 槽按原版合成台 18px 间距紧贴排布，整体放面板左半中部；
 * - 箭头复用 anvil_arrow.png 透明裁片（231b 已裁好，22×15）；
 * - 名称框回到最左 (8,8) 宽 158（没有锤子让位）。
 */
public final class DropLayout {

    private DropLayout() {}

    // ===== 2×2 输入槽（18px 紧贴间距，上排 0/1 下排 2/3）=====
    public static int slot0X() { return 36; }
    public static int slot0Y() { return 51; }
    public static int slot1X() { return 54; }
    public static int slot1Y() { return 51; }
    public static int slot2X() { return 36; }
    public static int slot2Y() { return 69; }
    public static int slot3X() { return 54; }
    public static int slot3Y() { return 69; }

    /** 结果槽：2×2 ends x72 → 箭头 (82,59) ends104 → 结果 120 起 ends138（距滚动列表 x172 安全）。 */
    public static int slotResultX() { return 120; }
    public static int slotResultY() { return 59; }

    /** 配方名称输入框：无锤子让位 → (8,8) 宽 158（ends166，滚动列表 x172 前 6px 间隙）。 */
    public static int nameX() { return 8; }
    public static int nameY() { return 8; }
    public static int nameWidth() { return 158; }

    /** 箭头（复用 231b 的 anvil_arrow.png 透明裁片 22×15）：2×2 ends x72 与结果框 starts x119 之间垂直居中。 */
    public static int arrowX() { return 82; }
    public static int arrowY() { return 60; }

    /** 右侧按钮列（新建/移除/保存/音效/绑定/清除绑定），与铁砧/熔炼同一列位。 */
    public static int btnX() { return 306; }
    public static int btnY() { return 12; }
    public static int btnSpacing() { return 22; }

    /** 233a「仅名字检查」开关（合成槽左边，2×2 槽 starts x36 前的空隙）。233d-UI7 上移与粒子钮对齐结果槽中心：结果槽 y59 center=68，两钮高18+2 间隔→pair top=49。 */
    public static int switchX() { return 8; }
    public static int switchY() { return 49; }
    public static int switchW() { return 18; }
    public static int switchH() { return 18; }

    /** 233d-UI7 音效图标钮：结果槽右边（120+18=138 右缘 +2 间隔），与结果槽同 y。 */
    public static int soundX() { return 140; }
    public static int soundY() { return 59; }

    /** 233d-UI7 粒子特效图标钮：「仅名字检查」正下方（49+18+2 间隔），同 x 竖列；pair center=68 对齐结果槽中心。 */
    public static int fxX() { return 8; }
    public static int fxY() { return 69; }

    /** 整个界面的整体偏移（与熔炼/锻造/铁砧同一既定值：用户确认过的全局面板位置）。 */
    public static int guiOffsetX() { return 0; }
    public static int guiOffsetY() { return 28; }
}
