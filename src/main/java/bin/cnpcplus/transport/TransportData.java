package bin.cnpcplus.transport;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.controllers.data.TransportCategory;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** 与 1.20.1 同名的视觉 NBT；位置权限、坐标与解锁数据仍由 CNPC 管理。 */
public final class TransportData {
    /** 传送点贴图目录（相对 assets 命名空间）。 */
    public static final String ROOT = "textures/gui/transporter/";
    /**
     * 名字版本号。可视化编辑改名时递增；原生"传送师"页保存时会把它打开时拿到的旧版本号
     * 一起带回来，服务端据此识别"这是旧快照"，避免把新名字回退。
     */
    public static final String NAME_STAMP = "NameStamp";
    private static final Map<TransportLocation, NBTTagCompound> LOCATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TransportCategory, NBTTagCompound> PANELS = Collections.synchronizedMap(new WeakHashMap<>());
    private TransportData() { }

    public static String key(String value) {
        if (value == null) return "";
        if (value.endsWith(".png")) value = value.substring(0, value.length() - 4);
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < value.length() && key.length() < 64; i++) {
            char c = value.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_' || c == '-') key.append(c);
        }
        return key.toString();
    }

    /**
     * 是不是"相对资源路径"（而不是内置图标名 / 上传图片的哈希）。
     * 判定：含 '/'、含 ':'、或以 .png 结尾。
     */
    public static boolean isPath(String value) {
        if (value == null || value.isEmpty()) return false;
        return value.indexOf('/') >= 0 || value.indexOf(':') >= 0 || value.toLowerCase(Locale.ROOT).endsWith(".png");
    }

    /**
     * 规范化玩家填的图片路径，返回 NULL 表示非法。
     * <ul>
     *   <li>{@code portal.png} / {@code custom/portal.png} → 相对 {@code textures/gui/transporter/}</li>
     *   <li>{@code mypack:textures/gui/transporter/portal.png} → 原样保留，方便整包替换</li>
     * </ul>
     * 材质包只要放 {@code assets/cnpcplus/textures/gui/transporter/custom/xxx.png} 就能直接生效。
     */
    public static String path(String value) {
        if (value == null) return "";
        String raw = value.trim().replace('\\', '/');
        while (raw.startsWith("/")) raw = raw.substring(1);
        if (raw.isEmpty() || raw.contains("..")) return "";
        int colon = raw.indexOf(':');
        if (colon >= 0) {
            String namespace = raw.substring(0, colon);
            String rest = raw.substring(colon + 1);
            while (rest.startsWith("/")) rest = rest.substring(1);
            if (!namespace.matches("[a-z0-9_.-]{1,32}")) return "";
            if (!rest.toLowerCase(Locale.ROOT).endsWith(".png")) rest += ".png";
            if (!rest.startsWith("textures/")) return "";
            return limit(namespace + ":" + rest, 160);
        }
        if (raw.startsWith(ROOT)) raw = raw.substring(ROOT.length());
        else if (raw.startsWith("textures/")) raw = raw.substring("textures/".length());
        if (!raw.toLowerCase(Locale.ROOT).endsWith(".png")) raw += ".png";
        return limit(ROOT + raw, 160);
    }

    /** 图片字段统一入口：路径走 {@link #path}，其余仍按内置图标名收敛。 */
    public static String image(String value) { return isPath(value) ? path(value) : key(value); }

    /** 路径 → 贴图位置；相对路径落在 cnpcplus 命名空间的传送点目录下。 */
    public static ResourceLocation resource(String value) {
        if (value != null && value.indexOf(':') >= 0) return new ResourceLocation(value);
        String path = value == null ? "" : value;
        if (!path.startsWith(ROOT)) path = ROOT + path;
        return new ResourceLocation("cnpcplus", path);
    }

    public static String limit(String value, int max) { return value == null ? "" : value.length() > max ? value.substring(0, max) : value; }
    public static int integer(NBTTagCompound tag, String key, int fallback, int min, int max) {
        return tag.hasKey(key, 99) ? Math.max(min, Math.min(max, tag.getInteger(key))) : fallback;
    }
    public static NBTTagCompound cleanEntry(NBTTagCompound source) {
        if (source == null) source = new NBTTagCompound();
        NBTTagCompound tag = new NBTTagCompound();
        for (String key : new String[]{"Icon", "IconSel", "Image"}) tag.setString(key, image(source.getString(key)));
        tag.setString("Desc", limit(source.getString("Desc"), 2048));
        tag.setString("DescSpans", limit(source.getString("DescSpans"), 8192));
        tag.setString("NameSpans", limit(source.getString("NameSpans"), 8192));
        tag.setInteger("ImageMode", integer(source, "ImageMode", 0, 0, 4));
        for (String key : new String[]{"IconSize", "ImageZoom", "ImgScale"}) tag.setInteger(key, integer(source, key, 100, 25, 400));
        for (String key : new String[]{"ImgX", "ImgY"}) tag.setInteger(key, integer(source, key, 0, -2000, 2000));
        for (String key : new String[]{"SrcW", "SrcH"}) tag.setInteger(key, integer(source, key, 0, 0, 2048));
        tag.setInteger("TextColor", integer(source, "TextColor", -1, -1, 0xFFFFFF));
        int size = source.hasKey("TextSize", 99) ? source.getInteger("TextSize") : -1;
        tag.setInteger("TextSize", size <= 0 ? -1 : Math.max(6, Math.min(48, size)));
        tag.setInteger(NAME_STAMP, Math.max(0, source.getInteger(NAME_STAMP)));
        return tag;
    }
    public static NBTTagCompound cleanPanel(NBTTagCompound source) {
        if (source == null) source = new NBTTagCompound();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("TabText", source.hasKey("TabText") ? limit(source.getString("TabText"), 16) : "传送");
        tag.setString("TabSpans", limit(source.getString("TabSpans"), 8192));
        for (String key : new String[]{"TabIcon", "CurIcon"}) tag.setString(key, image(source.getString(key)));
        String[] elements = {"Row", "Cur", "Intro", "Tab"}; int[] sizes = {12, 15, 13, 15};
        for (int i = 0; i < elements.length; i++) {
            String prefix = elements[i];
            tag.setInteger(prefix + "Size", integer(source, prefix + "Size", sizes[i], 6, 48));
            tag.setInteger(prefix + "Color", integer(source, prefix + "Color", i == 2 ? 0xE0E0E0 : 0xFFFFFF, 0, 0xFFFFFF));
            tag.setInteger(prefix + "X", integer(source, prefix + "X", 0, -120, 120));
            tag.setInteger(prefix + "Y", integer(source, prefix + "Y", 0, -120, 120));
        }
        for (String key : new String[]{"TabIconSize", "CurIconSize"}) tag.setInteger(key, integer(source, key, 100, 25, 400));
        return tag;
    }
    public static NBTTagCompound location(TransportLocation location) {
        NBTTagCompound tag = LOCATIONS.get(location);
        return tag == null ? cleanEntry(new NBTTagCompound()) : tag.copy();
    }
    public static void setLocation(TransportLocation location, NBTTagCompound tag) { LOCATIONS.put(location, cleanEntry(tag)); }
    public static int stamp(TransportLocation location) {
        NBTTagCompound tag = LOCATIONS.get(location);
        return tag == null ? 0 : Math.max(0, tag.getInteger(NAME_STAMP));
    }
    /** 名字被真正改动后调用：推进版本号，之后任何旧快照的名字都会被服务端丢弃。 */
    public static void bumpNameStamp(TransportLocation location) {
        NBTTagCompound tag = location(location);
        tag.setInteger(NAME_STAMP, Math.max(1, tag.getInteger(NAME_STAMP) + 1));
        LOCATIONS.put(location, tag);
    }
    /** 客户端补丁里的版本号可能偏低；至少保持在服务端已经见过的值。 */
    public static void ensureStamp(TransportLocation location, int minimum) {
        if (minimum <= 0 || stamp(location) >= minimum) return;
        NBTTagCompound tag = location(location);
        tag.setInteger(NAME_STAMP, minimum);
        LOCATIONS.put(location, tag);
    }
    public static NBTTagCompound panel(TransportCategory category) {
        NBTTagCompound tag = PANELS.get(category);
        return tag == null ? cleanPanel(new NBTTagCompound()) : tag.copy();
    }
    public static void setPanel(TransportCategory category, NBTTagCompound tag) { PANELS.put(category, cleanPanel(tag)); }
    public static NBTTagCompound meta(TransportLocation location) {
        NBTTagCompound tag = location(location);
        tag.setInteger("Id", location.id);
        tag.setString("Name", location.name);
        tag.setInteger(NAME_STAMP, stamp(location));
        return tag;
    }
}
