package top.cnpcplus;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import top.cnpcplus.config.network.PacketServerConfigSync;
import top.cnpcplus.config.network.PacketUpdateServerConfig;
import top.cnpcplus.wand.network.WandNpcPacket;

/** Execute the production codecs; no player permissions, real packet delivery or GUI/Mixin claim. */
public final class WandNetworkRegression {
    public static void main(String[] args) throws Exception {
        // Ordinary JavaExec has no Forge event constructor transformer. Remove ONLY channel bootstrap
        // in a child test loader; all production constructors, codecs and record methods stay unchanged.
        ClassLoader loader = new ClassLoader(WandNetworkRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("top.cnpcplus.WandNetworkRegression")
                        && !name.equals("top.cnpcplus.config.network.PacketUpdateServerConfig")
                        && !name.startsWith("top.cnpcplus.wand.network.WandNpcPacket")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) {
                        try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (stream == null) throw new ClassNotFoundException(name);
                            byte[] bytes = stream.readAllBytes();
                            if (name.equals("top.cnpcplus.wand.network.WandNpcPacket")) {
                                ClassWriter writer = new ClassWriter(0);
                                boolean[] found = {false};
                                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                                        if (method.equals("<clinit>")) { found[0] = true; return null; }
                                        return super.visitMethod(access, method, descriptor, signature, exceptions);
                                    }
                                }, 0);
                                check(found[0], "expected channel initializer missing");
                                bytes = writer.toByteArray();
                            } else if (name.equals("top.cnpcplus.config.network.PacketUpdateServerConfig")) {
                                // Retain the real authorized assignment branch; permission/player/IO/transport are separate.
                                ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
                                boolean found = false;
                                for (MethodNode method : node.methods) if (method.name.startsWith("lambda$handle$")) {
                                    AbstractInsnNode start = null, end = null;
                                    for (var instruction : method.instructions.toArray()) {
                                        if (instruction instanceof FieldInsnNode field && field.name.equals("followerTeleportRange") && start == null) start = field.getPrevious();
                                        if (instruction instanceof MethodInsnNode call && call.name.equals("save")) end = call.getPrevious();
                                    }
                                    check(start != null && end != null, "authorized config assignment branch missing");
                                    while (method.instructions.getFirst() != start) method.instructions.remove(method.instructions.getFirst());
                                    while (end != null) { var next = end.getNext(); method.instructions.remove(end); end = next; }
                                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                                    method.tryCatchBlocks.clear(); method.localVariables = null;
                                    for (var instruction : method.instructions.toArray()) if (instruction instanceof FrameNode) method.instructions.remove(instruction);
                                    found = true;
                                }
                                check(found, "config handler work lambda missing");
                                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); bytes = writer.toByteArray();
                            }
                            type = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException e) { throw new ClassNotFoundException(name, e); }
                    }
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        Method run = loader.loadClass(WandNetworkRegression.class.getName()).getDeclaredMethod("codecs");
        run.setAccessible(true); run.invoke(null);
    }

    private static void codecs() throws Exception {
        Method encode = WandNpcPacket.class.getDeclaredMethod("encode", FriendlyByteBuf.class);
        Method decode = WandNpcPacket.class.getDeclaredMethod("decode", FriendlyByteBuf.class);
        encode.setAccessible(true); decode.setAccessible(true);
        for (boolean copying : new boolean[]{false, true}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                WandNpcPacket original = new WandNpcPacket(41, Long.MAX_VALUE - 3, copying);
                encode.invoke(original, buf);
                check(original.equals(decode.invoke(null, buf)) && !buf.isReadable(), "wand request codec mismatch");
            } finally { buf.release(); }
        }
        encode = WandNpcPacket.Snapshot.class.getDeclaredMethod("encode", FriendlyByteBuf.class);
        decode = WandNpcPacket.Snapshot.class.getDeclaredMethod("decode", FriendlyByteBuf.class);
        encode.setAccessible(true); decode.setAccessible(true);
        for (boolean copying : new boolean[]{false, true}) for (boolean failed : new boolean[]{false, true}) {
            CompoundTag data = failed ? null : new CompoundTag();
            if (data != null) { data.putString("Name", "我是NPC的名字"); data.putLong("Marker", 9876543210L); }
            var original = new WandNpcPacket.Snapshot(41, 1234567890123L, copying, data);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                encode.invoke(original, buf);
                var decoded = (WandNpcPacket.Snapshot)decode.invoke(null, buf);
                check(original.equals(decoded) && !buf.isReadable(), "snapshot boolean/null codec mismatch");
                if (data != null) {
                    decoded.data().putString("Name", "副本");
                    check(data.getString("Name").equals("我是NPC的名字"), "codec returned shared mutable NBT");
                }
            } finally { buf.release(); }
        }
        for (boolean update : new boolean[]{false, true}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                // An old peer's custom migration string is consumed, discarded and never echoed as active config.
                buf.writeBoolean(true); buf.writeVarInt(63); buf.writeVarInt(9999);
                buf.writeUtf("已有自定义上限提示", 512);
                if (update) {
                    var packet = PacketUpdateServerConfig.decode(buf);
                    buf.clear(); packet.encode(buf);
                } else {
                    var packet = PacketServerConfigSync.decode(buf);
                    buf.clear(); packet.encode(buf);
                }
                check(buf.readBoolean() && buf.readVarInt() == 63 && buf.readVarInt() == 9999, "active config slots shifted");
                check(buf.readUtf(512).isEmpty() && !buf.isReadable(), "obsolete message slot changed or leaked");
            } finally { buf.release(); }
        }
        var spec = top.cnpcplus.config.CnpcPlusServerConfig.getConfig();
        spec.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        try {
            top.cnpcplus.config.CnpcPlusServerConfig.HireLimitMessage.set("已有自定义迁移台词");
            for (boolean update : new boolean[]{false, true}) {
                Object packet = update ? new PacketUpdateServerConfig(true, 64, 987) : new PacketServerConfigSync(true, 64, 987);
                Method work = java.util.Arrays.stream(packet.getClass().getDeclaredMethods()).filter(m -> m.getName().startsWith("lambda$handle$")).findFirst().orElseThrow();
                work.setAccessible(true);
                if (work.getParameterCount() == 0) work.invoke(packet); else work.invoke(packet, new Object[]{null});
                check(top.cnpcplus.config.CnpcPlusServerConfig.RealArmorEnabled.get()
                        && top.cnpcplus.config.CnpcPlusServerConfig.FollowerTeleportRange.get() == 64
                        && top.cnpcplus.config.CnpcPlusServerConfig.MaxHiredNpcs.get() == 987, "handler assignment branch did not execute");
                check(top.cnpcplus.config.CnpcPlusServerConfig.HireLimitMessage.get().equals("已有自定义迁移台词"), "handler overwrote legacy migration value");
            }
        } finally { spec.setConfig(null); }
        System.out.println("WAND_NETWORK_REGRESSION=PASS (production codecs; test-only channel bootstrap removed; delivery/permissions/GUI/Mixin untested)");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
