package top.cnpcplus.transport;

/** 传送点扩展 NBT 键与图片模式常量。键名与 TransportLocation 原生键不冲突。 */
public final class TransportKeys {
    private TransportKeys() {}

    public static final String ICON = "Icon";
    public static final String ICON_SEL = "IconSel";
    public static final String IMAGE = "Image";
    public static final String IMAGE_MODE = "ImageMode";
    public static final String DESC = "Desc";
    /** 241l：每点位图标等比尺寸（%，25-400）。 */
    public static final String ICON_SIZE = "IconSize";
    /** 241m：每点位图片等比缩放（%，25-400）。 */
    public static final String IMAGE_ZOOM = "ImageZoom";
    /** 241n：每点位文本颜色（RGB，-1=未设置=用面板默认）。 */
    public static final String TEXT_COLOR = "TextColor";
    /** 241n：每点位文本字号（%，-1=未设置=用面板默认）。 */
    public static final String TEXT_SIZE = "TextSize";
    /** 241l：改名用（entries 里的新名字，空=不改名）。 */
    public static final String NAME = "Name";
    /** 241n：删除标记（entries 里的名字=要删掉这个传送点）。 */
    public static final String DELETE = "Delete";
    /** 241q：图片在框内的平移（设计像素）。 */
    public static final String IMG_X = "ImgX";
    public static final String IMG_Y = "ImgY";
    /** 241q：图片在框内的缩放 %（100 = 按原比例铺满框）。 */
    public static final String IMG_SCALE = "ImgScale";
    /** 241q：源图像素宽高（客户端据此按比例画，避免只显示左上角）。 */
    public static final String SRC_W = "SrcW";
    public static final String SRC_H = "SrcH";
    /** 241q：文本样式区间（颜色/粗斜下划线），正文保持干净。 */
    public static final String DESC_SPANS = "DescSpans";
    /** 241t：传送点名字的样式区间（列表行文字 / 当前位置文字共用）。 */
    public static final String NAME_SPANS = "NameSpans";

    public static final int MODE_FILL = 0;
    public static final int MODE_STRETCH = 1;
    public static final int MODE_TILE = 2;
    public static final int MODE_CENTER = 3;
    public static final int MODE_FIT = 4;
    public static final int MODE_COUNT = 5;

    public static final String DIR_NAME = "cnpcplus_transport";
    public static final int MAX_IMAGE_BYTES = 4 * 1024 * 1024;
    public static final int MAX_IMAGE_DIM = 2048;

    /** 文件键净化：只允许 [A-Za-z0-9_-]，最长 64，防止路径穿越。 */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < raw.length() && sb.length() < 64; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
