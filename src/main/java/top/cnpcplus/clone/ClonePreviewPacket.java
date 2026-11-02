package top.cnpcplus.clone;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcs;
import noppes.npcs.CustomNpcsPermissions;

/** The worker owns only files and selection values; connection-bound delivery stays on the server thread. */
@EventBusSubscriber(modid = "cnpcplus")
public record ClonePreviewPacket(int slot, int tab, String name, long request) implements CustomPacketPayload {
    public static final Type<ClonePreviewPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "clone_preview"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClonePreviewPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeByte(p.slot); buf.writeInt(p.tab); buf.writeUtf(p.name, 256); buf.writeLong(p.request); },
            buf -> new ClonePreviewPacket(buf.readUnsignedByte(), buf.readInt(), buf.readUtf(256), buf.readLong()));
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "CNPCPlus-ClonePreview"); t.setDaemon(true); return t; });
    private static final Map<MinecraftServer, Map<UUID, Pending[]>> PENDING = new WeakHashMap<>();
    private static final byte[] EMPTY = new byte[0];
    static { IO.allowCoreThreadTimeOut(true); }
    private record Pending(net.minecraft.server.network.ServerGamePacketListenerImpl connection,
            int tab, String name, long request, long submitted, Future<byte[]> future) {}
    @Override public Type<ClonePreviewPacket> type() { return TYPE; }
    public static void send(int slot, int tab, String name, long nonce) { PacketDistributor.sendToServer(new ClonePreviewPacket(slot, tab, name, nonce)); }
    private static boolean allowed(ServerPlayer player) {
        if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)
                || CustomNpcs.OpsOnly && !player.server.getPlayerList().isOp(player.getGameProfile())) return false;
        var tool = player.getMainHandItem().getItem();
        if (tool == CustomItems.wand || tool == CustomItems.mount) return true;
        return tool == CustomItems.cloner && (CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.SPAWNER_MOB)
                || CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.SPAWNER_CREATE));
    }
    public static void handle(ClonePreviewPacket p, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || p.slot > 1) return;
            var pending = PENDING.computeIfAbsent(player.server, server -> new HashMap<>());
            Pending[] slots = pending.get(player.getUUID());
            if (slots == null) {
                if (pending.size() >= 128) return;
                slots = new Pending[2]; pending.put(player.getUUID(), slots);
            }
            Pending previous = slots[p.slot];
            if (previous != null && previous.future != null) previous.future.cancel(false);
            long now = System.nanoTime();
            if (previous != null && now - previous.submitted < TimeUnit.MILLISECONDS.toNanos(150)) {
                slots[p.slot] = new Pending(player.connection, p.tab, p.name, p.request, previous.submitted, null); return;
            }
            slots[p.slot] = new Pending(player.connection, p.tab, p.name, p.request, now, null);
            try {
                if (!allowed(player) || !ClonePreviewData.validSelection(p.tab, p.name)) { reply(player, p.tab, p.name, p.request, EMPTY); return; }
                File world = CustomNpcs.getLevelSaveDirectory();
                Future<byte[]> future = readAsync(new File(world, "clones"), world, p.tab, p.name);
                slots[p.slot] = new Pending(player.connection, p.tab, p.name, p.request, now, future);
            } catch (RuntimeException e) { reply(player, p.tab, p.name, p.request, EMPTY); }
        });
    }
    public static Future<byte[]> readAsync(File clones, File world, int tab, String name) {
        IO.purge(); return IO.submit(() -> ClonePreviewData.read(clones, world, tab, name));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Map<UUID, Pending[]> pending = PENDING.get(event.getServer());
        if (pending == null) return;
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); Pending[] slots = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            for (int slot = 0; slot < slots.length; slot++) {
                Pending value = slots[slot];
                if (value == null) continue;
                if (player == null || player.connection != value.connection) {
                    if (value.future != null) value.future.cancel(false); slots[slot] = null;
                } else if (value.future != null && (value.future.isDone() || System.nanoTime() - value.submitted > TimeUnit.SECONDS.toNanos(5))) {
                    byte[] bytes = EMPTY;
                    try { if (value.future.isDone() && allowed(player)) bytes = value.future.get(); }
                    catch (Exception e) { /* A matching empty reply clears the draft. */ }
                    value.future.cancel(false);
                    slots[slot] = new Pending(value.connection, value.tab, value.name, value.request, value.submitted, null);
                    reply(player, value.tab, value.name, value.request, bytes);
                }
            }
            if (slots[0] == null && slots[1] == null) iterator.remove();
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var pending = PENDING.get(player.server); Pending[] removed = pending == null ? null : pending.remove(player.getUUID());
        if (removed != null) for (Pending value : removed) if (value != null && value.future != null) value.future.cancel(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        var pending = PENDING.remove(event.getServer());
        if (pending != null) for (Pending[] slots : pending.values()) for (Pending value : slots) if (value != null && value.future != null) value.future.cancel(false);
        IO.purge();
    }
    private static void reply(ServerPlayer player, int tab, String name, long request, byte[] bytes) {
        if (NetworkRegistry.hasChannel(player.connection, Snapshot.TYPE.id())) PacketDistributor.sendToPlayer(player, new Snapshot(tab, name, request, bytes));
    }
    public record Snapshot(int tab, String name, long request, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cnpcplus", "clone_preview_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeInt(p.tab); buf.writeUtf(p.name, 256); buf.writeLong(p.request); buf.writeByteArray(p.bytes); },
                buf -> new Snapshot(buf.readInt(), buf.readUtf(256), buf.readLong(), buf.readByteArray(ClonePreviewData.MAX_BYTES)));
        @Override public Type<Snapshot> type() { return TYPE; }
        public static void handle(Snapshot p, IPayloadContext context) { context.enqueueWork(() -> top.cnpcplus.clone.client.ClonePreview.accept(p.tab, p.name, p.request, p.bytes)); }
    }
}
