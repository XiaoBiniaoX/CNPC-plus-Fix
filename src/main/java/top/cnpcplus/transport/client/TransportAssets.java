package top.cnpcplus.transport.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 传送面板资源定位与图标列表。 */
public final class TransportAssets {
    private TransportAssets() {}

    private static final String TEX = "textures/gui/transporter/";

    public static final ResourceLocation PANEL = rl("panel_main.png");
    public static final ResourceLocation TAB = rl("tab.png");
    public static final ResourceLocation TAB_STAR = rl("tab_star.png");
    public static final ResourceLocation SEARCH_BOX = rl("search_box.png");
    public static final ResourceLocation SEARCH_ICON = rl("search_icon.png");
    public static final ResourceLocation LIST_GUTTER = rl("list_gutter.png");
    public static final ResourceLocation ROW_NORMAL = rl("row_normal.png");
    public static final ResourceLocation ROW_BG = rl("row_bg.png");
    public static final ResourceLocation SCROLL_TRACK = rl("scroll_track.png");
    public static final ResourceLocation SCROLL_THUMB = rl("scroll_thumb.png");
    public static final ResourceLocation CURRENT_PANEL = rl("current_panel.png");
    public static final ResourceLocation CURRENT_ICON = rl("current_icon.png");
    public static final ResourceLocation IMAGE_FRAME = rl("image_frame.png");
    public static final ResourceLocation INTRO_PANEL = rl("intro_panel.png");
    public static final ResourceLocation DEFAULT_ICON = rl("default_icon.png");

    public static final ResourceLocation ICON_DIR = new ResourceLocation("cnpcplus", TEX + "icons/");

    public static final int ROW_TEX_W = 372, ROW_TEX_H = 64;
    public static final int ICON_TEX = 1920;
    public static final int CURRENT_ICON_TEX = 40;
    public static final int DEFAULT_ICON_TEX = 32;

    private static ResourceLocation rl(String path) {
        return new ResourceLocation("cnpcplus", TEX + path);
    }

    /** 图标 RL：cnpcplus:textures/gui/transporter/icons/&lt;name&gt;.png */
    public static ResourceLocation icon(String name) {
        if (name == null || name.isEmpty()) return DEFAULT_ICON;
        String safe = name.endsWith(".png") ? name.substring(0, name.length() - 4) : name;
        StringBuilder sb = new StringBuilder(safe.length());
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                sb.append(c);
            }
        }
        if (sb.length() == 0) return DEFAULT_ICON;
        return new ResourceLocation("cnpcplus", TEX + "icons/" + sb + ".png");
    }

    private static List<String> iconNames;

    /** 枚举全部图标文件名（无扩展名），缓存。 */
    public static synchronized List<String> iconNames() {
        if (iconNames != null) return iconNames;
        List<String> out = new ArrayList<>();
        try {
            ResourceManager rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            Map<ResourceLocation, Resource> found = rm.listResources(
                    "textures/gui/transporter/icons", rl -> rl.getPath().endsWith(".png"));
            for (ResourceLocation id : found.keySet()) {
                String file = id.getPath();
                int slash = file.lastIndexOf('/');
                if (slash >= 0) file = file.substring(slash + 1);
                if (file.endsWith(".png")) file = file.substring(0, file.length() - 4);
                if (!file.isEmpty()) out.add(file);
            }
        } catch (Throwable ignored) {
        }
        java.util.Collections.sort(out);
        iconNames = out;
        return iconNames;
    }
}
