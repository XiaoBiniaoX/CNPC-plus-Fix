package top.cnpcplus.transport.client;

/** 传送面板设计空间布局常量（952×535，源自排版与效果.png + 各贴图实测尺寸）。 */
public final class TransportLayout {
    private TransportLayout() {}

    public static final int DESIGN_W = 952;
    public static final int DESIGN_H = 535;

    public static final int TAB_X = 74, TAB_Y = -20, TAB_W = 184, TAB_H = 42;
    public static final int TAB_STAR_X = 90, TAB_STAR_Y = -11, TAB_STAR = 32;
    public static final int TAB_TEXT_X = 128, TAB_TEXT_Y = -6;

    public static final int SEARCH_X = 74, SEARCH_Y = 36, SEARCH_W = 404, SEARCH_H = 44;
    public static final int SEARCH_ICON_X = 86, SEARCH_ICON_Y = 46, SEARCH_ICON = 24;
    public static final int SEARCH_FIELD_X = 118, SEARCH_FIELD_Y = 44, SEARCH_FIELD_W = 350, SEARCH_FIELD_H = 28;

    public static final int LIST_X = 74, LIST_Y = 88, LIST_W = 404, LIST_H = 354;
    public static final int ROW_X = 86, ROW_Y = 98, ROW_W = 372, ROW_H = 64, ROW_STRIDE = 70;
    /** 241n：列表凹槽高度 354 / 行距 70 → 一页能放 5 行（第 5 行 378..442 正好贴底）。 */
    public static final int ROW_VISIBLE = 5;
    public static final int SCROLL_TRACK_X = 458, SCROLL_TRACK_Y = 96, SCROLL_TRACK_W = 20, SCROLL_TRACK_H = 340;
    public static final int SCROLL_THUMB_W = 16;

    public static final int CURRENT_X = 74, CURRENT_Y = 452, CURRENT_W = 404, CURRENT_H = 64;
    public static final int CURRENT_ICON_X = 86, CURRENT_ICON_Y = 464, CURRENT_ICON = 40;
    public static final int CURRENT_LABEL_X = 136, CURRENT_LABEL_Y = 462;
    public static final int CURRENT_NAME_X = 136, CURRENT_NAME_Y = 480;

    public static final int IMG_FRAME_X = 500, IMG_FRAME_Y = 44, IMG_FRAME_W = 440, IMG_FRAME_H = 282;
    public static final int IMG_PAD = 4;
    public static final int INTRO_X = 500, INTRO_Y = 342, INTRO_W = 440, INTRO_H = 178;
    public static final int INTRO_TEXT_X = 516, INTRO_TEXT_Y = 358, INTRO_TEXT_W = 408;

    public static final int BTN_ROW_ID_BASE = 100;
    public static final int BTN_SEARCH_ID = 50;
    public static final int BTN_TRAVEL_ID = 99;
    public static final int CLICK_MS = 400;
}
