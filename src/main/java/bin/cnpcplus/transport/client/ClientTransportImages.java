package bin.cnpcplus.transport.client;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.transport.PacketTransportImage;
import bin.cnpcplus.transport.PacketTransportMeta;
import bin.cnpcplus.transport.TransportImages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod.EventBusSubscriber(modid = CnpcPlus.MODID, value = Side.CLIENT)
public final class ClientTransportImages {
    public static final class Image {
        public final ResourceLocation texture; public final int width, height;
        Image(ResourceLocation texture, int width, int height) { this.texture = texture; this.width = width; this.height = height; }
    }
    private static final class Transfer {
        final byte[] data; int offset;
        Transfer(int length) { data = new byte[length]; }
    }
    private static final Map<String, Image> IMAGES = new LinkedHashMap<>(32, .75F, true);
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    private static final Map<String, Transfer> TRANSFERS = new HashMap<>();
    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cnpcplus-image-decode"); thread.setDaemon(true); return thread;
    });
    private static int generation;
    private static int nextTexture;
    private static long pixels;
    private static final Deque<ResourceLocation> FREE_TEXTURES = new ArrayDeque<>();
    private ClientTransportImages() { }

    public static Image get(int npcId, boolean editor, String key) {
        if (key == null || key.isEmpty()) return null;
        Image image = IMAGES.get(key);
        if (image != null) return image;
        long now = System.currentTimeMillis();
        if (now - REQUESTED.getOrDefault(key, 0L) > 10000) {
            REQUESTED.put(key, now);
            NBTTagCompound tag = new NBTTagCompound(); tag.setString("Key", key); tag.setBoolean("Editor", editor);
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketTransportMeta(npcId, PacketTransportMeta.IMAGE, tag));
        }
        return null;
    }
    public static void receive(PacketTransportImage message) {
        Transfer transfer = TRANSFERS.get(message.key);
        if (message.offset == 0) {
            if (TRANSFERS.size() >= 8) return;
            transfer = new Transfer(message.total); TRANSFERS.put(message.key, transfer);
        }
        if (transfer == null || transfer.offset != message.offset || transfer.data.length != message.total) return;
        System.arraycopy(message.bytes, 0, transfer.data, transfer.offset, message.bytes.length);
        transfer.offset += message.bytes.length;
        if (transfer.offset != transfer.data.length) return;
        TRANSFERS.remove(message.key);
        cache(message.key, transfer.data);
    }
    public static void cache(String key, byte[] bytes) {
        if (!TransportImages.validPng(bytes)) return;
        int expected = generation;
        DECODER.execute(() -> {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image == null) return;
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (generation != expected) return;
                    Image old = IMAGES.remove(key);
                    if (old != null) release(old);
                    long incoming = (long) image.getWidth() * image.getHeight();
                    while (!IMAGES.isEmpty() && (IMAGES.size() >= 32 || pixels + incoming > 16 * 1024 * 1024)) {
                        String first = IMAGES.keySet().iterator().next();
                        release(IMAGES.remove(first));
                    }
                    DynamicTexture texture = new DynamicTexture(image);
                    ResourceLocation location = FREE_TEXTURES.isEmpty()
                            ? new ResourceLocation("cnpcplus", "dynamic/transport_" + nextTexture++) : FREE_TEXTURES.removeFirst();
                    Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
                    Minecraft.getMinecraft().getTextureManager().loadTexture(location, texture);
                    IMAGES.put(key, new Image(location, image.getWidth(), image.getHeight())); pixels += incoming;
                });
            } catch (Exception error) { CnpcPlus.LOGGER.error("Cannot decode transport image {}", key, error); }
        });
    }
    private static void release(Image image) {
        pixels -= (long) image.width * image.height;
        Minecraft.getMinecraft().getTextureManager().deleteTexture(image.texture);
        FREE_TEXTURES.addLast(image.texture);
    }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        generation++;
        for (Image image : IMAGES.values()) release(image);
        IMAGES.clear(); REQUESTED.clear(); TRANSFERS.clear();
    }
}
