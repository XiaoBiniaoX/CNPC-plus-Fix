package top.cnpcplus.smithing;

/**
 * 锻造配方界面布局，全部坐标硬编码。
 * 放在 common 包，容器（服务端）也能安全引用。
 * 226：从 SmeltingLayout 复制，槽位换锻造台（火焰/箭头/4个图标开关坐标删除）。
 */
public final class SmithingLayout {

    private SmithingLayout() {}

    // ===== 槽位（锻造台）=====
    /**
     * 原版 SmithingMenu 槽位字节码实证：(8,48)(26,48)(44,48) 三输入紧邻（间距18）、结果 (98,48)（与第三槽差54）。
     * 把同一相对关系搬到本面板左区：起点沿用熔炼输入槽 x56、行 y55——
     * 避开名称框 y8..28、锤子(14,36..66)、箭头(117,56)、滚动列表 x172 起。
     */
    public static int slotTemplateX() { return 56; }
    public static int slotTemplateY() { return 55; }
    public static int slotBaseX() { return 74; }
    public static int slotBaseY() { return 55; }
    public static int slotAdditionX() { return 92; }
    public static int slotAdditionY() { return 55; }
    public static int slotResultX() { return 146; }
    public static int slotResultY() { return 55; }

    /** 配方名称输入框 */
    public static int nameX() { return 8; }
    public static int nameY() { return 8; }
    /** 227：时间/经验两框按用户要求删除（锻造台没有熔炼时间与经验），空出的区域改放锤子贴图。 */
    /**
     * 锻造台锤子贴图（原版 smithing.png (7,8) 30x30，像素扫描实证）：
     * 落在名称框(y8..28)下方、槽位行(x56 起)左侧的空地，不与任何控件重叠。
     */
    public static int hammerX() { return 14; }
    public static int hammerY() { return 36; }
    /** 锻造台箭头贴图（原版 smithing.png (68,49) 22x15）：升级材料槽(ends x110)→结果槽(x146) 缝隙中央。 */
    public static int arrowX() { return 117; }
    public static int arrowY() { return 56; }
    /**
     * 230 「仅名字检查」开关（照抄熔炼外观：20x20 图标按钮），放三个输入槽（x56 起）的左边。
     * 取 (14,68)：锤子贴图占 (14..44, 36..66)，其正下方 2px 起落开关 →
     * x14..34 完全在槽左侧（槽框 x55 起）且与锤子/名称框(y8..28)/箭头(x117) 零重叠。
     */
    public static int nameOnlyX() { return 14; }
    public static int nameOnlyY() { return 68; }
    public static int nameOnlyWidth() { return 20; }
    public static int nameOnlyHeight() { return 20; }
    /** 右侧按钮列（新建/移除/保存） */
    public static int btnX() { return 306; }
    public static int btnY() { return 12; }
    public static int btnSpacing() { return 22; }

    /** 整个界面的整体偏移（与熔炼版同一既定值：用户确认过的全局面板位置） */
    public static int guiOffsetX() { return 0; }
    public static int guiOffsetY() { return 28; }
}
