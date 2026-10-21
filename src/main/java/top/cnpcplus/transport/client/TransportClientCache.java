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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务241 客户端传送数据缓存：元数据 + 图片纹理 + 请求去重。
 * 面板与编辑器共用；渲染每帧从这里取，图片到达后下一帧自然显示，无需监听器。
 */
public final class TransportClientCache {
    private TransportClientCache() {}

    private static final Map<String, CompoundTag> META = new ConcurrentHashMap<>();
    private static final Map<String, DynamicTexture> IMAGES = new ConcurrentHashMap<>();
    private static final Set<String> IMAGE_PENDING = ConcurrentHashMap.newKeySet();

    private static volatile CompoundTag root;
    private static volatile String currentName = "";

    public static void acceptMeta(CompoundTag data) {
        if (data == null) return;
        root = data;
        currentName = data.getString("Current");
        CompoundTag entries = data.getCompound("Entries");
        META.clear();
        for (String key : entries.getAllKeys()) {
            META.put(key, entries.getCompound(key));
        }
        String img = data.getCompound("CurrentMeta").getString("Image");
        if (!img.isEmpty()) requestImage(img);
        for (CompoundTag m : META.values()) {
            String i = m.getString("Image");
            if (!i.isEmpty()) requestImage(i);
        }
    }

    public static void acceptImage(String key, byte[] bytes) {
        IMAGE_PENDING.remove(key);
        if (bytes == null || bytes.length == 0) return;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
            if (img.getWidth() > TransportKeys.MAX_IMAGE_DIM || img.getHeight() > TransportKeys.MAX_IMAGE_DIM) {
                img.close();
                return;
            }
            ResourceLocation id = imageId(key);
            DynamicTexture tex = new DynamicTexture(img);
            DynamicTexture old = IMAGES.put(key, tex);
            if (old != null) old.close();
            Minecraft.getInstance().getTextureManager().register(id, tex);
        } catch (Throwable ignored) {
        }
    }

    public static void requestImage(String key) {
        String safe = TransportKeys.sanitize(key);
        if (safe.isEmpty()) return;
        if (IMAGES.containsKey(safe) || !IMAGE_PENDING.add(safe)) return;
        TransportPacketHandler.CHANNEL.sendToServer(new PacketTransportImageRequest(safe));
    }

    public static void requestMeta(int npcId) { requestMeta(npcId, false); }
    public static void requestMeta(int npcId, boolean editor) {
        TransportPacketHandler.CHANNEL.sendToServer(new PacketTransportMetaRequest(npcId, editor));
    }

    public static CompoundTag root() { return root; }
    public static String currentName() { return currentName; }
    public static CompoundTag meta(String name) { return name == null ? null : META.get(name); }
    public static Map<String, CompoundTag> allMeta() { return META; }

    public static boolean hasImage(String key) {
        return key != null && !key.isEmpty() && IMAGES.containsKey(key);
    }

    public static ResourceLocation imageId(String key) {
        return key == null || key.isEmpty() ? null
                : new ResourceLocation("cnpcplus", "transport/img_" + key.hashCode());
    }

    public static void clear() {
        META.clear();
        for (DynamicTexture t : IMAGES.values()) {
            try { t.close(); } catch (Throwable ignored) {}
        }
        IMAGES.clear();
        IMAGE_PENDING.clear();
        root = null;
        currentName = "";
    }
}
