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
 *
 * <p>233d-UI9 哈基彬要求重排：合成槽位/箭头/结果槽整体左移 28
 * （x36/54→8/26、结果 120→92、箭头 82→54）；音效/仅名字检查/粒子效果编辑
 * 三钮改放结果槽右边 x112 竖列 (112,39|59|79)。
 */
public final class DropLayout {

    private DropLayout() {}

    // ===== 2×2 输入槽（18px 紧贴间距，上排 0/1 下排 2/3；UI9 整体左移28）=====
    public static int slot0X() { return 8; }
    public static int slot0Y() { return 51; }
    public static int slot1X() { return 26; }
    public static int slot1Y() { return 51; }
    public static int slot2X() { return 8; }
    public static int slot2Y() { return 69; }
    public static int slot3X() { return 26; }
    public static int slot3Y() { return 69; }

    /** 结果槽（UI9）：2×2 ends x44 → 箭头 (54,60) ends76 → 结果 92 起 ends110 → 右列三钮 x112 起（滚动列表 x172 安全）。 */
    public static int slotResultX() { return 92; }
    public static int slotResultY() { return 59; }

    /** 配方名称输入框：无锤子让位 → (8,8) 宽 158（ends166，滚动列表 x172 前 6px 间隙）。 */
    public static int nameX() { return 8; }
    public static int nameY() { return 8; }
    public static int nameWidth() { return 158; }

    /** 箭头（复用 231b 的 anvil_arrow.png 透明裁片 22×15；UI9 随槽组左移28）：2×2 ends x44 与结果框 starts x91 之间垂直居中。 */
    public static int arrowX() { return 54; }
    public static int arrowY() { return 60; }

    /** 右侧按钮列（新建/移除/保存/音效/绑定/清除绑定），与铁砧/熔炼同一列位。 */
    public static int btnX() { return 306; }
    public static int btnY() { return 12; }
    public static int btnSpacing() { return 22; }

    /**
     * 233d-UI9 右侧竖列三钮（放结果槽右边：结果 ends110 → x112 起 2px 间隙）：
     * 自上而下＝音效(39) / 仅名字检查(59) / 粒子效果编辑(79)，钮 18 高、2px 间隔，
     * 列 ends y97；与名称框(y8..24)/滚动列表(x172)/背包(y113) 零重叠。
     */
    /** 「仅名字检查」开关（UI9 竖列中位，原 (8,49) 合成槽左边） */
    public static int switchX() { return 112; }
    public static int switchY() { return 59; }
    public static int switchW() { return 18; }
    public static int switchH() { return 18; }

    /** 音效图标钮（UI9 竖列首位，原 (140,59) 结果槽右侧同行） */
    public static int soundX() { return 112; }
    public static int soundY() { return 39; }

    /** 粒子特效图标钮（UI9 竖列末位，原 (8,69) 仅名字正下方） */
    public static int fxX() { return 112; }
    public static int fxY() { return 79; }

    /** 整个界面的整体偏移（与熔炼/锻造/铁砧同一既定值：用户确认过的全局面板位置）。 */
    public static int guiOffsetX() { return 0; }
    public static int guiOffsetY() { return 28; }
}
