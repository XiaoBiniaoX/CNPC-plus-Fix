package bin.cnpcplus.transport.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TransportAssets {
    public static final List<String> ICONS = new ArrayList<>();
    private static final Map<String, ResourceLocation> RESOURCES = new HashMap<>();
    /** 资源包里的 PNG 尺寸；null 表示确认不存在。资源重载时整体清空。 */
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    static {
        try (InputStream in = TransportAssets.class.getResourceAsStream("/assets/cnpcplus/textures/gui/transporter/icons.txt")) {
            if (in != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line; while ((line = reader.readLine()) != null) if (!line.trim().isEmpty()) ICONS.add(line.trim());
            }
        } catch (java.io.IOException error) { bin.cnpcplus.CnpcPlus.LOGGER.error("Cannot read transport icon list", error); }
    }
    private TransportAssets() { }
    public static ResourceLocation resource(String name) { return RESOURCES.computeIfAbsent(name, value -> new ResourceLocation("cnpcplus", "textures/gui/transporter/" + value)); }
    public static void draw(String name, int x, int y, int width, int height) { draw(resource(name), x, y, width, height); }
    public static void draw(ResourceLocation texture, int x, int y, int width, int height) {
        GlStateManager.color(1, 1, 1, 1);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, width, height);
    }

    /**
     * 这个贴图在（模组 + 任意资源包）里存不存在。材质包只要把文件放到
     * {@code assets/cnpcplus/textures/gui/transporter/custom/xxx.png} 就会被发现，
     * 不需要改配置，也不需要重启游戏（F3+T 后重新探测）。
     */
    public static boolean exists(ResourceLocation texture) { return size(texture) != null; }

    /**
     * 读 PNG 头里的宽高（IHDR 偏移 16/20），拿不到就是 null。
     * 用它既判断"文件存在"，也给路径贴图算绘制尺寸——不走上传通道也能显示。
     */
    public static int[] size(ResourceLocation texture) {
        synchronized (SIZES) {
            if (SIZES.containsKey(texture)) return SIZES.get(texture);
        }
        int[] size = null;
        try (InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(texture).getInputStream()) {
            DataInputStream data = new DataInputStream(stream);
            for (int i = 0; i < 16; i++) data.readUnsignedByte();
            int width = data.readInt(), height = data.readInt();
            if (width > 0 && height > 0 && width <= 8192 && height <= 8192) size = new int[]{width, height};
        } catch (Exception ignored) { }
        synchronized (SIZES) { SIZES.put(texture, size); }
        return size;
    }

    /** 资源包/语言重载后尺寸与存在性都可能变化。 */
    public static void clear() {
        synchronized (SIZES) { SIZES.clear(); }
    }
}
