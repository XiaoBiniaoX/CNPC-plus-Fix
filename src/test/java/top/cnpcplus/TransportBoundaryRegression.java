package top.cnpcplus;

import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import top.cnpcplus.transport.client.TransportClientCache;

/** Production reassembly/codecs; test-only sender, authorization, disk and acknowledgement boundaries. */
public final class TransportBoundaryRegression {
    public static UUID player = UUID.randomUUID();
    public static byte[] saved;
    public static ServerPlayer sender(NetworkEvent.Context context) { return null; }
    public static boolean allowed(ServerPlayer sender, int level) { return true; }
    public static UUID uuid(ServerPlayer sender) { return player; }
    public static boolean write(String key, byte[] bytes) { saved = bytes; return true; }
    public static void image(String key, byte[] bytes) { saved = bytes; }

    public static void main(String[] args) throws Exception {
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        ClassLoader loader = new ClassLoader(TransportBoundaryRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("top.cnpcplus.transport.network.PacketTransportImageChunk")
                        && !name.equals("top.cnpcplus.transport.network.PacketTransportImageResponse")) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                    for (MethodNode method : node.methods) for (var instruction : method.instructions.toArray())
                        if (instruction instanceof MethodInsnNode call && call.owner.equals("top/cnpcplus/transport/client/TransportClientCache") && call.name.equals("acceptImage")) {
                            call.owner = "top/cnpcplus/TransportBoundaryRegression"; call.name = "image";
                        }
                    for (MethodNode method : node.methods) if (method.name.startsWith("lambda$handle$") && method.desc.equals("(Ljava/util/function/Supplier;)V")) {
                        AbstractInsnNode start = null, ack = null;
                        for (var instruction : method.instructions.toArray()) {
                            if (instruction instanceof MethodInsnNode call) {
                                if (call.name.equals("getSender")) { call.setOpcode(Opcodes.INVOKESTATIC); call.owner = "top/cnpcplus/TransportBoundaryRegression"; call.name = "sender"; call.desc = "(Lnet/minecraftforge/network/NetworkEvent$Context;)Lnet/minecraft/server/level/ServerPlayer;"; }
                                else if (call.name.equals("hasPermissions")) { call.setOpcode(Opcodes.INVOKESTATIC); call.owner = "top/cnpcplus/TransportBoundaryRegression"; call.name = "allowed"; call.desc = "(Lnet/minecraft/server/level/ServerPlayer;I)Z"; }
                                else if (call.name.equals("getUUID")) { call.setOpcode(Opcodes.INVOKESTATIC); call.owner = "top/cnpcplus/TransportBoundaryRegression"; call.name = "uuid"; call.desc = "(Lnet/minecraft/server/level/ServerPlayer;)Ljava/util/UUID;"; }
                                else if (call.owner.equals("top/cnpcplus/transport/TransportImageIO") && call.name.equals("write")) {
                                    call.owner = "top/cnpcplus/TransportBoundaryRegression";
                                    for (var cursor = call.getNext(); cursor != null; cursor = cursor.getNext()) {
                                        if (cursor instanceof VarInsnNode store && store.getOpcode() == Opcodes.ISTORE) { ack = cursor.getNext(); break; }
                                    }
                                }
                                else if (call.name.equals("sanitize")) start = call.getPrevious().getPrevious();
                            }
                        }
                        if (start == null || ack == null) throw new AssertionError("authorized reassembly/disk branch missing");
                        while (method.instructions.getFirst() != start) method.instructions.remove(method.instructions.getFirst());
                        InsnList sender = new InsnList(); sender.add(new InsnNode(Opcodes.ACONST_NULL)); sender.add(new VarInsnNode(Opcodes.ASTORE, 2));
                        method.instructions.insertBefore(start, sender);
                        while (ack != null) { var next = ack.getNext(); method.instructions.remove(ack); ack = next; }
                        method.instructions.add(new InsnNode(Opcodes.RETURN));
                        method.tryCatchBlocks.clear(); method.localVariables = null;
                        for (var instruction : method.instructions.toArray()) if (instruction instanceof FrameNode) method.instructions.remove(instruction);
                    }
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); node.accept(writer);
                    byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
                if (resolve) resolveClass(loaded); return loaded;
            }
        };
        Class<?> packet = loader.loadClass("top.cnpcplus.transport.network.PacketTransportImageChunk");
        var constructor = packet.getConstructor(String.class, int.class, byte[].class);
        // The work lambda is the only non-static handle lambda taking Supplier.
        var work = Arrays.stream(packet.getDeclaredMethods()).filter(m -> m.getName().startsWith("lambda$handle$") && Arrays.equals(m.getParameterTypes(), new Class<?>[]{Supplier.class})).findFirst().orElseThrow();
        work.setAccessible(true);
        UUID first = player, second = UUID.randomUUID();
        work.invoke(constructor.newInstance("shared", 0, new byte[]{1}), new Object[]{null});
        player = second; work.invoke(constructor.newInstance("shared", 0, new byte[]{2}), new Object[]{null});
        player = first; work.invoke(constructor.newInstance("shared", 1, new byte[0]), new Object[]{null});
        check(Arrays.equals(saved, new byte[]{1}) != baseline, "two-player same-key upload contract");
        if (!baseline) { player = second; work.invoke(constructor.newInstance("shared", 1, new byte[0]), new Object[]{null}); check(Arrays.equals(saved, new byte[]{2}), "second upload lost"); }
        saved = null;
        work.invoke(constructor.newInstance("partial", 0, new byte[]{9}), new Object[]{null});
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        boolean rejected = false;
        try {
            buf.writeUtf("partial", 64); buf.writeVarInt(1); buf.writeVarInt(-1);
            try { Object malformed = packet.getMethod("decode", FriendlyByteBuf.class).invoke(null, buf); work.invoke(malformed, new Object[]{null}); }
            catch (java.lang.reflect.InvocationTargetException failure) { if (!(failure.getCause() instanceof IllegalArgumentException)) throw failure; rejected = true; }
        } finally { buf.release(); }
        check(rejected != baseline && (baseline ? Arrays.equals(saved, new byte[]{9}) : saved == null), "malformed chunk submitted partial image");
        TransportClientCache.setMetaListener(() -> {}); TransportClientCache.clear();
        var listener = TransportClientCache.class.getDeclaredField("metaListener"); listener.setAccessible(true);
        check((listener.get(null) == null) != baseline, "world cleanup retained page listener");
        if (!baseline) {
            saved = null;
            player = first; work.invoke(constructor.newInstance("logout", 0, new byte[]{3}), new Object[]{null});
            player = second; work.invoke(constructor.newInstance("logout", 0, new byte[]{4}), new Object[]{null});
            packet.getMethod("clearPlayer", UUID.class).invoke(null, first);
            player = first; work.invoke(constructor.newInstance("logout", 1, new byte[0]), new Object[]{null});
            check(saved == null, "logged out partial upload survived");
            player = second; work.invoke(constructor.newInstance("logout", 1, new byte[0]), new Object[]{null});
            check(Arrays.equals(saved, new byte[]{4}), "logout removed another player upload");
            Runnable old = () -> {}, current = () -> {};
            TransportClientCache.setMetaListener(current); TransportClientCache.clearMetaListener(old);
            check(listener.get(null) == current, "old page removed current listener");
            TransportClientCache.clearMetaListener(current); check(listener.get(null) == null, "page listener not released");
            CompoundTag ack = new CompoundTag(); ack.putBoolean("Saved", true); TransportClientCache.acceptMeta(ack);
            ack = new CompoundTag(); ack.putBoolean("ImageSaved", true); TransportClientCache.acceptMeta(ack);
            TransportClientCache.clear(); check(!TransportClientCache.consumeSavedAck() && !TransportClientCache.consumeImageAck(), "logout retained acknowledgement");
            Class<?> response = loader.loadClass("top.cnpcplus.transport.network.PacketTransportImageResponse");
            var receive = response.getDeclaredMethod("receive", String.class, int.class, boolean.class, byte[].class); receive.setAccessible(true);
            saved = null; receive.invoke(null, "download", 0, false, new byte[]{5}); receive.invoke(null, "download", 1, true, new byte[]{6});
            check(Arrays.equals(saved, new byte[]{5, 6}), "download reassembly lost bytes");
            saved = null; receive.invoke(null, "download", 0, false, new byte[]{5}); receive.invoke(null, "download", 2, true, new byte[]{6});
            check(saved == null, "out-of-order download submitted partial image");
            for (Class<?> codec : List.of(packet, response)) for (int length : new int[]{-1, 32769, 2}) {
                FriendlyByteBuf malformed = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    malformed.writeUtf("invalid", 64); malformed.writeVarInt(0);
                    if (codec == response) malformed.writeBoolean(true);
                    malformed.writeVarInt(length); malformed.writeByte(1);
                    try { codec.getMethod("decode", FriendlyByteBuf.class).invoke(null, malformed); throw new AssertionError("invalid length accepted: " + length); }
                    catch (java.lang.reflect.InvocationTargetException failure) { if (!(failure.getCause() instanceof IllegalArgumentException)) throw failure; }
                } finally { malformed.release(); }
            }
            receive.invoke(null, "unfinished", 0, false, new byte[]{7}); response.getMethod("clear").invoke(null);
            for (String name : List.of("BUFFERS", "NEXT_SEQ")) { var field = response.getDeclaredField(name); field.setAccessible(true); check(((Map<?,?>)field.get(null)).isEmpty(), "download cleanup retained " + name); }
            packet.getMethod("clear").invoke(null);
            for (String name : List.of("BUFFERS", "NEXT_SEQ")) { var field = packet.getDeclaredField(name); field.setAccessible(true); check(((Map<?,?>)field.get(null)).isEmpty(), "server cleanup retained upload " + name); }
        }
        System.out.println(baseline ? "TRANSPORT_BOUNDARY_BASELINE=REPRODUCED same-key player interference/malformed-end partial commit/page listener retention" : "TRANSPORT_BOUNDARY_REGRESSION=PASS sender-key isolation/invalid decode/no partial commit/player-download cleanup/listener ownership/acks (authorization/disk/ack/texture substituted; not game packet delivery)");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
