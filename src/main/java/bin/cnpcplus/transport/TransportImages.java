package bin.cnpcplus.transport;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.data.NbtFiles;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import noppes.npcs.CustomNpcs;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TransportImages {
    private static final ExecutorService READER = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "cnpcplus-transport-images"); thread.setDaemon(true); return thread;
    });
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final class Upload {
        final String key; final byte[] data; int position; long updated;
        Upload(String key, int length) { this.key = key; this.data = new byte[length]; }
    }
    private TransportImages() { }
    private static Path file(String key) { return CustomNpcs.getWorldSaveDirectory().toPath().resolve("cnpcplus_transport").resolve(TransportData.key(key) + ".png"); }

    public static boolean validPng(byte[] data) {
        if (data.length < 24 || data.length > PacketTransportImage.MAX_BYTES) return false;
        ByteBuffer header = ByteBuffer.wrap(data);
        if (header.getLong() != 0x89504E470D0A1A0AL || header.getInt(12) != 0x49484452) return false;
        int width = header.getInt(16), height = header.getInt(20);
        return width > 0 && height > 0 && width <= 2048 && height <= 2048;
    }
    public static String hash(byte[] bytes) {
        try {
            StringBuilder text = new StringBuilder(64);
            for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) text.append(Character.forDigit((value >>> 4) & 15, 16)).append(Character.forDigit(value & 15, 16));
            return text.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    public static void receive(EntityPlayerMP player, PacketTransportImage message) {
        if (PacketTransportMeta.npc(player, message.npcId, true) == null || !message.key.matches("[0-9a-f]{64}")) return;
        long now = System.currentTimeMillis();
        Upload upload = UPLOADS.get(player.getUniqueID());
        if (message.offset == 0) { upload = new Upload(message.key, message.total); UPLOADS.put(player.getUniqueID(), upload); }
        if (upload == null || !upload.key.equals(message.key) || upload.data.length != message.total || upload.position != message.offset
                || upload.position > 0 && now - upload.updated > 30000) { UPLOADS.remove(player.getUniqueID()); return; }
        System.arraycopy(message.bytes, 0, upload.data, upload.position, message.bytes.length);
        upload.position += message.bytes.length; upload.updated = now;
        if (upload.position != upload.data.length) return;
        UPLOADS.remove(player.getUniqueID());
        if (!validPng(upload.data) || !hash(upload.data).equals(upload.key)) { acknowledge(player, message.npcId, message.key, false); return; }
        NbtFiles.writeBytes(file(upload.key), upload.data).whenComplete((done, error) -> player.getServerWorld().addScheduledTask(() -> {
            if (error != null) CnpcPlus.LOGGER.error("Cannot save transport image {}", message.key, error);
            acknowledge(player, message.npcId, message.key, error == null);
        }));
    }

    private static void acknowledge(EntityPlayerMP player, int npcId, String key, boolean success) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("ImageKey", key); tag.setBoolean("ImageSaved", success);
        CraftingViewNetwork.CHANNEL.sendTo(new PacketTransportMeta(npcId, PacketTransportMeta.ACK, tag), player);
    }

    public static void send(EntityPlayerMP player, int npcId, String key) {
        Path file = file(key);
        READER.execute(() -> {
            try {
                if (!Files.isRegularFile(file) || Files.size(file) > PacketTransportImage.MAX_BYTES) return;
                byte[] bytes = Files.readAllBytes(file);
                if (!validPng(bytes)) return;
                player.getServerWorld().addScheduledTask(() -> {
                    for (int offset = 0; offset < bytes.length; offset += PacketTransportImage.CHUNK) {
                        CraftingViewNetwork.CHANNEL.sendTo(new PacketTransportImage(npcId, key, offset, bytes), player);
                    }
                });
            } catch (Exception error) { CnpcPlus.LOGGER.error("Cannot read transport image {}", key, error); }
        });
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { UPLOADS.remove(event.player.getUniqueID()); }
    public static void clear() { UPLOADS.clear(); }
}
