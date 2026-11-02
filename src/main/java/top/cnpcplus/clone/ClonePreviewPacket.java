package top.cnpcplus.clone;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import noppes.npcs.CustomItems;
import noppes.npcs.CustomNpcs;
import noppes.npcs.CustomNpcsPermissions;

/** The IO worker owns only files and immutable selection data; delivery stays on the server thread. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public record ClonePreviewPacket(int slot, int tab, String name, long request) {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("cnpcplus", "clone_preview"), () -> "2", "2"::equals, "2"::equals);
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), r -> { Thread t = new Thread(r, "CNPCPlus-ClonePreview"); t.setDaemon(true); return t; });
    private static final Map<MinecraftServer, Map<UUID, Pending[]>> PENDING = new WeakHashMap<>();
    private static final byte[] EMPTY = new byte[0];
    static { IO.allowCoreThreadTimeOut(true); }
    private record Pending(net.minecraft.server.network.ServerGamePacketListenerImpl connection,
            int tab, String name, long request, long submitted, Future<byte[]> future) {}

    public static void init() {
        CHANNEL.registerMessage(0, ClonePreviewPacket.class, ClonePreviewPacket::encode, ClonePreviewPacket::decode,
                ClonePreviewPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, Snapshot::encode, Snapshot::decode,
                Snapshot::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(int slot, int tab, String name, long request) { CHANNEL.sendToServer(new ClonePreviewPacket(slot, tab, name, request)); }
    private void encode(FriendlyByteBuf buf) { buf.writeByte(slot); buf.writeInt(tab); buf.writeUtf(name, 256); buf.writeLong(request); }
    private static ClonePreviewPacket decode(FriendlyByteBuf buf) { return new ClonePreviewPacket(buf.readUnsignedByte(), buf.readInt(), buf.readUtf(256), buf.readLong()); }

    private static boolean allowed(ServerPlayer player) {
        if (!CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.NPC_GUI)) return false;
        if (CustomNpcs.OpsOnly && !player.server.getPlayerList().isOp(player.getGameProfile())) return false;
        var tool = player.getMainHandItem().getItem();
        if (tool == CustomItems.wand || tool == CustomItems.mount) return true;
        return tool == CustomItems.cloner && (CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.SPAWNER_MOB)
                || CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.SPAWNER_CREATE));
    }

    private void handle(Supplier<NetworkEvent.Context> ctx) {
        var context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || slot < 0 || slot > 1) return;
            var pending = PENDING.computeIfAbsent(player.server, server -> new HashMap<>());
            Pending[] slots = pending.get(player.getUUID());
            if (slots == null) {
                if (pending.size() >= 128) return;
                slots = new Pending[2];
                pending.put(player.getUUID(), slots);
            }
            Pending previous = slots[slot];
            if (previous != null && previous.future != null) previous.future.cancel(false);
            long now = System.nanoTime();
            if (previous != null && now - previous.submitted < TimeUnit.MILLISECONDS.toNanos(150)) {
                slots[slot] = new Pending(player.connection, tab, name, request, previous.submitted, null);
                return; // Bound replies too: an abusive stream must not amplify outbound traffic.
            }
            slots[slot] = new Pending(player.connection, tab, name, request, now, null);
            try {
                if (!allowed(player) || !ClonePreviewData.validSelection(tab, name)) {
                    reply(player, tab, name, request, EMPTY);
                    return;
                }
                File world = CustomNpcs.getLevelSaveDirectory();
                File clones = new File(world, "clones");
                Future<byte[]> future = readAsync(clones, world, tab, name);
                slots[slot] = new Pending(player.connection, tab, name, request, now, future);
            } catch (Exception e) {
                reply(player, tab, name, request, EMPTY);
            }
        });
        context.setPacketHandled(true);
    }
    public static Future<byte[]> readAsync(File clones, File world, int tab, String name) {
        IO.purge();
        return IO.submit(() -> ClonePreviewData.read(clones, world, tab, name));
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Map<UUID, Pending[]> pending = PENDING.get(event.getServer());
        if (pending == null) return;
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Pending[] slots = entry.getValue();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            for (int slot = 0; slot < slots.length; slot++) {
                Pending value = slots[slot];
                if (value == null) continue;
                if (player == null || player.connection != value.connection) {
                    if (value.future != null) value.future.cancel(false);
                    slots[slot] = null;
                } else if (value.future != null && (value.future.isDone()
                    || System.nanoTime() - value.submitted > TimeUnit.SECONDS.toNanos(5))) {
                    byte[] bytes = EMPTY;
                    try { if (value.future.isDone() && allowed(player)) bytes = value.future.get(); }
                    catch (Exception e) { /* Explicit empty reply below. */ }
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
        var pending = PENDING.get(player.server);
        Pending[] removed = pending == null ? null : pending.remove(player.getUUID());
        if (removed != null) for (Pending value : removed) if (value != null && value.future != null) value.future.cancel(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        var pending = PENDING.remove(event.getServer());
        if (pending != null) for (Pending[] slots : pending.values())
            for (Pending value : slots) if (value != null && value.future != null) value.future.cancel(false);
        IO.purge();
    }
    private static void reply(ServerPlayer player, int tab, String name, long request, byte[] bytes) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(tab, name, request, bytes));
    }
    public record Snapshot(int tab, String name, long request, byte[] bytes) {
        private void encode(FriendlyByteBuf buf) { buf.writeInt(tab); buf.writeUtf(name, 256); buf.writeLong(request); buf.writeByteArray(bytes); }
        private static Snapshot decode(FriendlyByteBuf buf) { return new Snapshot(buf.readInt(), buf.readUtf(256), buf.readLong(), buf.readByteArray(ClonePreviewData.MAX_BYTES)); }
        private void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> top.cnpcplus.clone.client.ClonePreview.accept(tab, name, request, bytes)));
            ctx.get().setPacketHandled(true);
        }
    }
}
