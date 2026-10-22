package top.cnpcplus.transport.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.network.PacketTransportImageRequest;
import top.cnpcplus.transport.network.PacketTransportMetaRequest;
import top.cnpcplus.transport.network.TransportPacketHandler;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务241 客户端传送数据缓存：元数据 + 图片纹理 + 请求去重。
 * 面板与编辑器共用；渲染每帧从这里取，图片到达后下一帧自然显示，无需监听器。
 */
public final class TransportClientCache {
    private TransportClientCache() {}

    private static final Map<String, CompoundTag> META = new ConcurrentHashMap<>();
    /** 241l：编辑页专用缓存（玩家页响应不得清空它，否则编辑页列表会少条目）。 */
    private static final Map<String, CompoundTag> EDIT_META = new ConcurrentHashMap<>();
    private static final Map<String, DynamicTexture> IMAGES = new ConcurrentHashMap<>();
    /** 241t：源图像素尺寸，**按贴图 id 索引**（241t：之前按文件键索引，键名一旦对不上就退化成"左上角"）。 */
    private static final Map<ResourceLocation, int[]> IMG_SIZE = new ConcurrentHashMap<>();
    /** 241z：图片请求节流（可重试），替代旧的"一次性闸门"。 */
    private static final Map<String, Long> IMAGE_REQUESTED = new ConcurrentHashMap<>();

    private static volatile CompoundTag root;
    private static volatile String currentName = "";
    private static volatile CompoundTag panel = new CompoundTag();
    private static volatile Runnable metaListener;
    private static volatile boolean savedAck;
    private static volatile boolean imageAckOk;

    /** 241o：取走一次"已保存"回执标记。 */
    public static boolean consumeSavedAck() {
        boolean v = savedAck;
        savedAck = false;
        return v;
    }

    /** 241r：取走一次"图片上传"回执（true=成功）。 */
    public static boolean consumeImageAck() {
        boolean v = imageAckOk;
        imageAckOk = false;
        return v;
    }

    public static void acceptMeta(CompoundTag data) { acceptMeta(data, false); }

    public static void acceptMeta(CompoundTag data, boolean editor) {
        if (data == null) return;
        // 241o：服务端保存回执（只带 Saved 标记，不含条目数据 → 不能覆盖客户端的条目缓存）
        if (data.contains("Saved")) {
            Runnable l = metaListener;
            if (l != null) l.run();
            savedAck = true;
            return;
        }
        // 241r：图片上传回执
        if (data.contains("ImageSaved")) {
            imageAckOk = data.getBoolean("ImageSaved");
            Runnable l = metaListener;
            if (l != null) l.run();
            return;
        }
        root = data;
        currentName = data.getString("Current");
        CompoundTag p = data.getCompound("Panel");
        panel = p == null ? new CompoundTag() : p;
        CompoundTag entries = data.getCompound("Entries");
        Map<String, CompoundTag> target = editor ? EDIT_META : META;
        target.clear();
        for (String key : entries.getAllKeys()) {
            CompoundTag m = entries.getCompound(key);
            // 241n：把正文里遗留的旧格式码自动转成样式区间（正文保持干净）
            top.cnpcplus.transport.TransportSpans.cleanLegacy(m);
            target.put(key, m);
        }
        // 页签文本同样清理
        top.cnpcplus.transport.TransportSpans.cleanLegacy(panel);
        String img = data.getCompound("CurrentMeta").getString("Image");
        if (!img.isEmpty()) requestImage(img);
        for (CompoundTag m : target.values()) {
            String i = m.getString("Image");
            if (!i.isEmpty()) requestImage(i);
        }
        Runnable l = metaListener;
        if (l != null) l.run();
    }

    /** 241l：meta 到达时通知（编辑页据此重建列表，否则列表永远停在打开时的空集）。 */
    public static void setMetaListener(Runnable r) { metaListener = r; }

    public static void acceptImage(String key, byte[] bytes) {
        String safe = TransportKeys.sanitize(key);
        if (safe.isEmpty()) return;
        IMAGE_REQUESTED.remove(safe);
        if (bytes == null || bytes.length == 0) return;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
            if (img.getWidth() > TransportKeys.MAX_IMAGE_DIM || img.getHeight() > TransportKeys.MAX_IMAGE_DIM) {
                img.close();
                return;
            }
            ResourceLocation id = imageId(safe);
            DynamicTexture tex = new DynamicTexture(img);
            DynamicTexture old = IMAGES.put(safe, tex);
            if (old != null) old.close();
            // 241z：**必须用 ResourceLocation 作键**（之前写成 id.toString() → 永远查不到 → 编辑页不画）
            IMG_SIZE.put(id, new int[]{img.getWidth(), img.getHeight()});
            Minecraft.getInstance().getTextureManager().register(id, tex);
            top.cnpcplus.transport.TransportLog.info(
                    "[cnpcplus] transport image received key=" + safe + " size="
                            + img.getWidth() + "x" + img.getHeight());
        } catch (Throwable ignored) {
        }
    }

    /**
     * 241z：请求图片字节。
     * 旧实现是"一次性闸门"（IMAGE_PENDING）→ **第一次请求失败（文件还没上传成功）后，
     * key 永远留在 pending 里 → 之后所有请求被静默吞掉 → 客户端永远拿不到图 → 帧一直是空的**。
     * 现在改成**节流可重试**：每 3 秒最多重发一次，拿到字节即停。
     */
    public static void requestImage(String key) {
        String safe = TransportKeys.sanitize(key);
        if (safe.isEmpty()) return;
        if (IMAGES.containsKey(safe)) return;
        long now = System.currentTimeMillis();
        long last = IMAGE_REQUESTED.getOrDefault(safe, 0L);
        if (now - last < 3000L) return;
        IMAGE_REQUESTED.put(safe, now);
        TransportPacketHandler.CHANNEL.sendToServer(new PacketTransportImageRequest(safe));
    }

    public static void requestMeta(int npcId) { requestMeta(npcId, false); }
    public static void requestMeta(int npcId, boolean editor) {
        TransportPacketHandler.CHANNEL.sendToServer(new PacketTransportMetaRequest(npcId, editor));
    }

    public static CompoundTag root() { return root; }
    public static String currentName() { return currentName; }
    public static CompoundTag panel() { return panel; }
    public static CompoundTag meta(String name) { return name == null ? null : META.get(name); }
    public static Map<String, CompoundTag> allMeta() { return META; }
    /** 241l：编辑页条目（独立缓存）。 */
    public static CompoundTag editMeta(String name) { return name == null ? null : EDIT_META.get(name); }
    public static Map<String, CompoundTag> allEditMeta() { return EDIT_META; }

    public static boolean hasImage(String key) {
        String safe = TransportKeys.sanitize(key);
        return !safe.isEmpty() && IMAGES.containsKey(safe);
    }

    public static ResourceLocation imageId(String key) {
        String safe = TransportKeys.sanitize(key);
        return safe.isEmpty() ? null
                : new ResourceLocation("cnpcplus", "transport/img_" + safe.hashCode());
    }

    /** 241z：源图像素尺寸（按文件键查 → 换算成贴图 id 查）。 */
    public static int[] imageSize(String key) {
        return IMG_SIZE.get(imageId(key));
    }

    /** 241t：按贴图 id 查源图尺寸（绘制时用这个，不可能对错）。 */
    public static int[] imageSizeOf(ResourceLocation id) {
        return id == null ? null : IMG_SIZE.get(id);
    }

    public static void clear() {
        META.clear();
        EDIT_META.clear();
        for (DynamicTexture t : IMAGES.values()) {
            try { t.close(); } catch (Throwable ignored) {}
        }
        IMAGES.clear();
        IMG_SIZE.clear();
        IMAGE_REQUESTED.clear();
        root = null;
        currentName = "";
        panel = new CompoundTag();
    }
}
