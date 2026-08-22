package top.cnpcplus.smelting;

/**
 * 熔炼配方界面布局，全部坐标硬编码。
 * 放在 common 包，容器（服务端）也能安全引用。
 */
public final class SmeltingLayout {

    private SmeltingLayout() {}

    // ===== 硬编码（用户 2026-08-19 确认满意的布局，勿改） =====
    /** 实际槽位·被熔炼物槽 */
    public static int slotInputX() { return 56; }
    public static int slotInputY() { return 35; }
    /** 实际槽位·燃料槽（用户 config 实测值 56,70） */
    public static int slotFuelX() { return 56; }
    public static int slotFuelY() { return 70; }
    /** 实际槽位·熔炼物输出槽 */
    public static int slotOutputX() { return 110; }
    public static int slotOutputY() { return 55; }
    /** 火焰贴图 */
    public static int flameX() { return 57; }
    public static int flameY() { return 55; }
    /** 熔炼进度箭头贴图 */
    public static int arrowX() { return 79; }
    public static int arrowY() { return 55; }
    /** 三个图标开关（高炉/烟熏炉/通用燃料），原 -10 已出界，右移到面板内 */
    public static int toggleX() { return 14; }
    public static int toggleY() { return 40; }
    public static int toggleSpacing() { return 22; }
    public static int toggleWidth() { return 20; }
    public static int toggleHeight() { return 20; }
    /** 配方名称输入框 */
    public static int nameX() { return 8; }
    public static int nameY() { return 8; }
    /**
     * 熔炼时间输入框。可用竖直区间只有 y28~36：
     * 上方名称框占 y8..28（8+高20），下方输出槽背景占 y54..72，而本框 x100..133 与输出槽 x109..127 有重叠。
     * 取 32 → 占 y32..50，上下各留 4 像素余量。
     */
    public static int timeX() { return 100; }
    public static int timeY() { return 32; }
    /** 熔炼经验输入框 */
    public static int xpX() { return 100; }
    public static int xpY() { return 80; }
    /** 时间/经验输入框宽高（宽度 = 原 25 的 4/3） */
    public static int fieldWidth() { return 33; }
    public static int fieldHeight() { return 18; }
    /** 右侧按钮列（新建/移除/保存） */
    public static int btnX() { return 306; }
    public static int btnY() { return 12; }
    public static int btnSpacing() { return 22; }

    /**
     * 整个界面（背景板+顶部菜单+全部控件+槽位）的整体偏移。
     * 这两个值原本走 config，目的只是让用户按自己的视角调参；用户 2026-08-22 实测确认 X=0 / Y=28
     * 最合适，调参使命结束，按约定收回硬编码（config 项已同步移除，避免旧配置文件里的
     * 过时值继续覆盖代码默认值 —— 之前正是文件里残留的 Y=10 让界面一直偏上）。
     */
    public static int guiOffsetX() { return 0; }
    public static int guiOffsetY() { return 28; }
}
