package top.cnpcplus.anvil;

/**
 * 自定义铁砧配方界面布局，全部坐标硬编码（阶段231 从 SmithingLayout 复制改三槽+铁砧装饰）。
 * 放在 common 包，容器（服务端）也能安全引用。
 *
 * 槽位与 UI 不一致的教训（SmeltingLayout/SmithingLayout 注释链）：
 * 1. m_7856_ 会被二次调用，界面偏移一律用「居中基准+偏移」重算，禁止 += 累加；
 * 2. 槽背景框必须按容器真实 slotRenderX/Y 画（drawSlotBgAt），不写死；
 * 3. 开关/输入框坐标先盘周围控件（名称框、槽行、滚动列表 x172 起、按钮列 x306）再取空隙。
 *
 * 231b 用户三连反馈重排（对照 vanilla anvil.png 实测 uv）：
 * - 三槽按原版铁砧相对位置拉开 27/76/134（原 y47→我们 y55，行偏移 +8），
 *   槽间贴 anvil.png 的 plus / arrow 透明裁片（资源 assets/cnpcplus/textures/gui/anvil_*.png）；
 * - 左上装饰换「大白锤子」（原版 anvil.png 标题行左上 uv(17,7)30x30 裁片），
 *   名称框右移让位（42..168）；
 * - 经验输入框挪结果槽下方 (134,75) 宽 36（用户嫌 40 太长）；
 * - 「仅名字检查」开关随结果槽右移 (152,51)：结果框 ends151 → 开关 152..172，
 *   滚动列表 x172 起，正好零间隙不重叠。
 */
public final class AnvilLayout {

    private AnvilLayout() {}

    // ===== 槽位（原版铁砧相对布局：A + plus + B + arrow + 结果）=====
    /** A(27)、B(76)、结果(134) = vanilla AnvilMenu INPUT/ADDITIONAL/RESULT X；y=47+8=55（面板行下移8） */
    public static int slotAX() { return 27; }
    public static int slotAY() { return 55; }
    public static int slotBX() { return 76; }
    public static int slotBY() { return 55; }
    public static int slotResultX() { return 134; }
    public static int slotResultY() { return 55; }

    /** 配方名称输入框：锤子 (8..38) 右侧让位 → 42..168（宽126） */
    public static int nameX() { return 42; }
    public static int nameY() { return 8; }
    public static int nameWidth() { return 126; }

    /**
     * 大白锤子装饰（vanilla anvil.png 裁片 30x30，背景已透明化）：
     * (8,6)..(38,36)——名称框同行左侧、A槽框(y54)上方零重叠。
     */
    public static int hammerX() { return 8; }
    public static int hammerY() { return 6; }

    /**
     * 经验输入框：结果槽正下方 (134,75) 36x20（用户指定+嫌40长）——
     * 134..170 距滚动列表 x172 零重叠、y75..95 在槽行(y54..73)下方、背包(y113)上方。
     */
    public static int expFieldX() { return 134; }
    public static int expFieldY() { return 75; }
    public static int expFieldWidth() { return 36; }
    public static int expFieldHeight() { return 20; }

    /**
     * 「仅名字检查」开关：结果槽右边（用户230指定，231b 随结果槽挪位）——
     * 结果框 ends x151 → 开关 (152,51) ends172，滚动列表 x172 起，1px 间隙零重叠。
     */
    public static int nameOnlyX() { return 152; }
    public static int nameOnlyY() { return 51; }
    public static int nameOnlyWidth() { return 20; }
    public static int nameOnlyHeight() { return 20; }

    // ===== 槽间贴图（vanilla anvil.png 实测 uv，行偏移+8；资源=透明裁片）=====
    /** plus：uv(53,49)13x13 → (53,57)，A框(ends44)..B框(starts75) 正中 */
    public static int plusX() { return 53; }
    public static int plusY() { return 57; }
    /** arrow：uv(102,48)22x15 → (102,56)，B框(ends93)..结果框(starts133) 正中 */
    public static int arrowX() { return 102; }
    public static int arrowY() { return 56; }

    /** 右侧按钮列（新建/移除/保存） */
    public static int btnX() { return 306; }
    public static int btnY() { return 12; }
    public static int btnSpacing() { return 22; }

    /** 整个界面的整体偏移（与熔炼/锻造同一既定值：用户确认过的全局面板位置） */
    public static int guiOffsetX() { return 0; }
    public static int guiOffsetY() { return 28; }
}
