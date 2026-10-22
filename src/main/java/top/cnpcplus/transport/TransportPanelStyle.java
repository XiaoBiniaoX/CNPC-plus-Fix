package top.cnpcplus.transport;

import net.minecraft.nbt.CompoundTag;

/**
 * 任务241j：传送面板样式（页签文本/页签图标/当前位置图标/4处字号）的 NBT 键、默认值与读写工具。
 * 样式挂在 TransportCategory（随 transport.dat 落盘），客户端从 MetaResponse 的 Panel 取。
 */
public final class TransportPanelStyle {
    private TransportPanelStyle() {}

    public static final String NBT_KEY = "cnpcplusPanel";
    public static final String TAB_TEXT = "TabText";
    public static final String TAB_ICON = "TabIcon";
    public static final String CUR_ICON = "CurIcon";
    public static final String ROW_SIZE = "RowSize";
    public static final String CUR_SIZE = "CurSize";
    public static final String INTRO_SIZE = "IntroSize";
    public static final String TAB_SIZE = "TabSize";

    /** 241l：文字颜色（RGB）。 */
    public static final String ROW_COLOR = "RowColor";
    public static final String CUR_COLOR = "CurColor";
    public static final String INTRO_COLOR = "IntroColor";
    public static final String TAB_COLOR = "TabColor";
    /** 241q：页签文本的样式区间（颜色/粗斜下划线）。 */
    public static final String TAB_SPANS = "TabSpans";
    /** 241l：元素 XY 偏移。 */
    public static final String ROW_X = "RowX";
    public static final String ROW_Y = "RowY";
    public static final String CUR_X = "CurX";
    public static final String CUR_Y = "CurY";
    public static final String INTRO_X = "IntroX";
    public static final String INTRO_Y = "IntroY";
    public static final String TAB_X = "TabX";
    public static final String TAB_Y = "TabY";
    /** 241l：图标等比尺寸（%）。 */
    public static final String TAB_ICON_SIZE = "TabIconSize";
    public static final String CUR_ICON_SIZE = "CurIconSize";

    public static final int DEFAULT_ROW_COLOR = 0xFFFFFF;
    public static final int DEFAULT_CUR_COLOR = 0xFFFFFF;
    public static final int DEFAULT_INTRO_COLOR = 0xE0E0E0;
    public static final int DEFAULT_TAB_COLOR = 0xFFFFFF;

    public static final int MIN_OFFSET = -120;
    public static final int MAX_OFFSET = 120;
    public static final int MIN_ICON_PCT = 25;
    public static final int MAX_ICON_PCT = 400;
    public static final int DEFAULT_ICON_PCT = 100;

    public static final String DEFAULT_TAB_TEXT = "传送";
    public static final int DEFAULT_ROW_SIZE = 12;
    public static final int DEFAULT_CUR_SIZE = 15;
    public static final int DEFAULT_INTRO_SIZE = 13;
    public static final int DEFAULT_TAB_SIZE = 15;

    public static final int MIN_SIZE = 6;
    public static final int MAX_SIZE = 48;
    public static final int MAX_TAB_TEXT = 16;

    public static int clampSize(int v) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, v));
    }

    public static int size(CompoundTag tag, String key, int def) {
        if (tag == null || !tag.contains(key)) return def;
        int v = tag.getInt(key);
        return v < MIN_SIZE || v > MAX_SIZE ? def : v;
    }

    public static void putSize(CompoundTag tag, String key, int v) {
        if (tag != null) tag.putInt(key, clampSize(v));
    }

    public static String str(CompoundTag tag, String key, String def) {
        if (tag == null || !tag.contains(key)) return def;
        String v = tag.getString(key);
        return v == null ? def : v;
    }

    public static String tabText(CompoundTag tag) {
        String v = str(tag, TAB_TEXT, DEFAULT_TAB_TEXT);
        return v.length() > MAX_TAB_TEXT ? v.substring(0, MAX_TAB_TEXT) : v;
    }

    public static String tabIcon(CompoundTag tag) {
        return TransportKeys.sanitize(str(tag, TAB_ICON, ""));
    }

    public static String curIcon(CompoundTag tag) {
        return TransportKeys.sanitize(str(tag, CUR_ICON, ""));
    }

    public static int rowSize(CompoundTag tag) { return size(tag, ROW_SIZE, DEFAULT_ROW_SIZE); }
    public static int curSize(CompoundTag tag) { return size(tag, CUR_SIZE, DEFAULT_CUR_SIZE); }
    public static int introSize(CompoundTag tag) { return size(tag, INTRO_SIZE, DEFAULT_INTRO_SIZE); }
    public static int tabSize(CompoundTag tag) { return size(tag, TAB_SIZE, DEFAULT_TAB_SIZE); }

    // 241l：颜色 / 坐标 / 图标尺寸

    public static int color(CompoundTag tag, String key, int def) {
        if (tag == null || !tag.contains(key)) return def;
        return tag.getInt(key) & 0xFFFFFF;
    }

    public static void putColor(CompoundTag tag, String key, int rgb) {
        if (tag != null) tag.putInt(key, rgb & 0xFFFFFF);
    }

    public static int rowColor(CompoundTag t) { return color(t, ROW_COLOR, DEFAULT_ROW_COLOR); }
    public static int curColor(CompoundTag t) { return color(t, CUR_COLOR, DEFAULT_CUR_COLOR); }
    public static int introColor(CompoundTag t) { return color(t, INTRO_COLOR, DEFAULT_INTRO_COLOR); }
    public static int tabColor(CompoundTag t) { return color(t, TAB_COLOR, DEFAULT_TAB_COLOR); }

    public static int offset(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) return 0;
        int v = tag.getInt(key);
        return v < MIN_OFFSET || v > MAX_OFFSET ? 0 : v;
    }

    public static void putOffset(CompoundTag tag, String key, int v) {
        if (tag != null) tag.putInt(key, Math.max(MIN_OFFSET, Math.min(MAX_OFFSET, v)));
    }

    public static int rowX(CompoundTag t) { return offset(t, ROW_X); }
    public static int rowY(CompoundTag t) { return offset(t, ROW_Y); }
    public static int curX(CompoundTag t) { return offset(t, CUR_X); }
    public static int curY(CompoundTag t) { return offset(t, CUR_Y); }
    public static int introX(CompoundTag t) { return offset(t, INTRO_X); }
    public static int introY(CompoundTag t) { return offset(t, INTRO_Y); }
    public static int tabX(CompoundTag t) { return offset(t, TAB_X); }
    public static int tabY(CompoundTag t) { return offset(t, TAB_Y); }

    public static int iconPct(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) return DEFAULT_ICON_PCT;
        int v = tag.getInt(key);
        return v < MIN_ICON_PCT || v > MAX_ICON_PCT ? DEFAULT_ICON_PCT : v;
    }

    public static void putIconPct(CompoundTag tag, String key, int v) {
        if (tag != null) tag.putInt(key, Math.max(MIN_ICON_PCT, Math.min(MAX_ICON_PCT, v)));
    }

    public static int tabIconSize(CompoundTag t) { return iconPct(t, TAB_ICON_SIZE); }
    public static int curIconSize(CompoundTag t) { return iconPct(t, CUR_ICON_SIZE); }

    public static void merge(CompoundTag target, CompoundTag src) {
        if (target == null || src == null) return;
        target.putString(TAB_TEXT, tabText(src));
        target.putString(TAB_ICON, tabIcon(src));
        target.putString(CUR_ICON, curIcon(src));
        putSize(target, ROW_SIZE, rowSize(src));
        putSize(target, CUR_SIZE, curSize(src));
        putSize(target, INTRO_SIZE, introSize(src));
        putSize(target, TAB_SIZE, tabSize(src));
        putColor(target, ROW_COLOR, rowColor(src));
        putColor(target, CUR_COLOR, curColor(src));
        putColor(target, INTRO_COLOR, introColor(src));
        putColor(target, TAB_COLOR, tabColor(src));
        putOffset(target, ROW_X, rowX(src));
        putOffset(target, ROW_Y, rowY(src));
        putOffset(target, CUR_X, curX(src));
        putOffset(target, CUR_Y, curY(src));
        putOffset(target, INTRO_X, introX(src));
        putOffset(target, INTRO_Y, introY(src));
        putOffset(target, TAB_X, tabX(src));
        putOffset(target, TAB_Y, tabY(src));
        putIconPct(target, TAB_ICON_SIZE, tabIconSize(src));
        putIconPct(target, CUR_ICON_SIZE, curIconSize(src));
        target.putString(TAB_SPANS, src.contains(TAB_SPANS) ? src.getString(TAB_SPANS) : "");
    }
}
