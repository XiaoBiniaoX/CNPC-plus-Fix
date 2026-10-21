package top.cnpcplus.transport;

/** 传送点扩展 NBT 键与图片模式常量。键名与 TransportLocation 原生键不冲突。 */
public final class TransportKeys {
    private TransportKeys() {}

    public static final String ICON = "Icon";
    public static final String ICON_SEL = "IconSel";
    public static final String IMAGE = "Image";
    public static final String IMAGE_MODE = "ImageMode";
    public static final String DESC = "Desc";

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
