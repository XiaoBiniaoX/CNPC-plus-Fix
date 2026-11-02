package top.cnpcplus;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import top.cnpcplus.clone.ClonePreviewData;
import top.cnpcplus.marks.MarkRotation;
import top.cnpcplus.mixin.MixinMarkRotation;
import top.cnpcplus.wand.WandTools;

/** Real codec/IO/rotation/metadata and nonce branches; no game, scripts or optional addons execute. */
public final class FinalFeaturesRegression {
    private static final class Rotation extends MixinMarkRotation {}
    private static final sun.misc.Unsafe UNSAFE;
    static {
        try { Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); UNSAFE = (sun.misc.Unsafe)field.get(null); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    public static final class ReturnedApi {
        static { if (System.nanoTime() != 0) throw new AssertionError("optional API initialized"); }
        public void setModel(String name) { throw new AssertionError("API invoked"); }
        public int mode;
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "Temp/CNPCplus-final-20261101/feature-regression" : args[0]);
        Files.createDirectories(output);
        Path root = Files.createTempDirectory(output, "run-");
        rotations();
        snapshots(root);
        metadata();
        lifecycle();
        previewMapping();
        check(WandTools.matches("zhaohuanshi", "召唤师") && WandTools.matches("zhs", "召唤师"), "quick-select pinyin lost");
        System.out.println("FINAL_FEATURES_REGRESSION=PASS (real IO/codec/rotation/metadata/nonce; input/render/transport still need runtime)");
    }
    private static void rotations() {
        Rotation first = new Rotation(), second = new Rotation();
        MarkRotation.read(first, new CompoundTag());
        check(!first.cnpcplus$horizontal() && !first.cnpcplus$vertical(), "legacy mark spins");
        first.cnpcplus$rotation(true, true);
        CompoundTag saved = new CompoundTag(); MarkRotation.write(first, saved);
        MarkRotation.read(second, saved.copy());
        first.cnpcplus$rotation(false, true);
        check(second.cnpcplus$horizontal() && second.cnpcplus$vertical(), "mark rotations shared");
        check(!first.cnpcplus$horizontal() && first.cnpcplus$vertical(), "independent axes lost");
    }
    private static void snapshots(Path root) throws Exception {
        for (String name : List.of("..", "../x", "x/y", "x\\y", "C:x", "x:stream", "x.", "CON", "nul.json", "COM1", "x\n"))
            check(!ClonePreviewData.validSelection(1, name), "path accepted: " + name);
        check(!ClonePreviewData.validSelection(0, "Npc") && !ClonePreviewData.validSelection(10, "Npc"), "invalid tab accepted");
        check(ClonePreviewData.validSelection(9, "召唤师 ' [正常]"), "legal name rejected");
        CompoundTag saved = new CompoundTag();
        saved.putString("Name", "测试NPC"); saved.putString("id", "customnpcs:customnpc"); saved.putString("Scripts", "do not run");
        saved.putInt("NpcJob", 9); saved.putInt("MoveSpeed", 5); saved.putFloat("PositionOffsetY", 0.5f);
        CompoundTag puppet = new CompoundTag(); puppet.putFloat("RotationX", 0.25f); saved.put("PuppetBody", puppet);
        CompoundTag model = new CompoundTag(); model.putInt("Type", 2); saved.put("NpcModelData", model);
        CompoundTag snapshot = ClonePreviewData.snapshot(saved);
        check(snapshot.getInt("Job") == 9 && snapshot.getCompound("Puppet").getCompound("PuppetBody").getFloat("RotationX") == 0.25f, "puppet root lost");
        check(snapshot.getInt("Speed") == 5 && snapshot.getFloat("PositionYOffset") == 0.5f, "spawn renames lost");
        check(!snapshot.contains("Scripts") && snapshot.getCompound("ModelData").getInt("Type") == 2, "script leak/model conversion");
        model.putInt("Type", 8); check(snapshot.getCompound("ModelData").getInt("Type") == 2, "snapshot aliases save");
        check(snapshot.equals(ClonePreviewData.decode(ClonePreviewData.encode(snapshot))), "codec roundtrip");
        check(ClonePreviewData.decode(new byte[0]) == null && ClonePreviewData.decode(new byte[ClonePreviewData.MAX_BYTES + 1]) == null, "invalid codec size accepted");
        saved.putFloat("Size", Float.NaN); expectFailure(() -> ClonePreviewData.snapshot(saved)); saved.remove("Size");
        saved.putInt("NpcJob", 6); check(!ClonePreviewData.snapshot(saved).contains("Puppet"), "non-puppet job loaded");
        expectFailure(() -> ClonePreviewData.validateText("[".repeat(65) + "]".repeat(65)));
        ClonePreviewData.validateText("{Name:'单双引号\"混合[]{}',Size:5}");
        expectFailure(() -> ClonePreviewData.validateText("{Name:'\"',x:" + "[".repeat(65) + "]".repeat(65) + "}"));
        expectFailure(() -> ClonePreviewData.validateText("{\"x\": \"unfinished}"));
        Path clones = Files.createDirectories(root.resolve("clones/1"));
        Path file = clones.resolve("测试.json");
        String text = "{\"Name\":\"测试NPC\",\"Size\":5.0f,\"NpcJob\":9,\"PuppetBody\":{\"RotationX\":0.25f},\"NpcModelData\":{\"Type\":2},\"LineColors\":[I;1,2,3]}";
        Files.writeString(file, text);
        byte[] before = Files.readAllBytes(file);
        byte[] bytes = ClonePreviewData.read(root.resolve("clones").toFile(), root.toFile(), 1, "测试");
        check(bytes.length > 0 && ClonePreviewData.decode(bytes).getString("Name").equals("测试NPC"), "native CNPC text unreadable");
        check(java.util.Arrays.equals(before, Files.readAllBytes(file)), "preview modified clone");
        check(ClonePreviewData.read(root.resolve("clones").toFile(), root.toFile(), 1, "absent").length == 0, "missing clone fallback");
        UUID id = UUID.randomUUID(); Path store = Files.createDirectories(root.resolve("data/cnpcplus/inventory"));
        CompoundTag external = new CompoundTag(); external.putString("Owner", UUID.randomUUID().toString());
        CompoundTag data = new CompoundTag(); ListTag armor = new ListTag(); CompoundTag item = new CompoundTag(); item.putInt("Slot", 2); armor.add(item); data.put("Armor", armor);
        external.put("Data", data); NbtIo.writeCompressed(external, store.resolve(id + ".dat").toFile());
        byte[] blob = Files.readAllBytes(store.resolve(id + ".dat"));
        Files.writeString(file, "{\"Name\":\"外置\",\"CNPCPlusInvId\":\"" + id + "\"}");
        bytes = ClonePreviewData.read(root.resolve("clones").toFile(), root.toFile(), 1, "测试");
        check(bytes.length > 0 && ClonePreviewData.decode(bytes).getList("Armor", 10).size() == 1, "external equipment unreadable");
        check(java.util.Arrays.equals(blob, Files.readAllBytes(store.resolve(id + ".dat"))), "preview modified external store");
        Files.writeString(file, "{\"Name\":\"内置\",\"CNPCPlusInvId\":\"" + id + "\",\"Armor\":[]}");
        check(ClonePreviewData.decode(ClonePreviewData.read(root.resolve("clones").toFile(), root.toFile(), 1, "测试")).getList("Armor", 10).isEmpty(), "external overwrote inline");
        Files.writeString(file, "{\"CNPCPlusInvId\":\"bad\"}");
        check(ClonePreviewData.read(root.resolve("clones").toFile(), root.toFile(), 1, "测试").length == 0, "invalid store reference accepted");
        Files.writeString(file, "{\"Name\":\"" + "a".repeat(4 * 1024 * 1024) + "\"}");
        check(ClonePreviewData.read(root.resolve("clones").toFile(), root.toFile(), 1, "测试").length == 0, "oversized file accepted");
        Files.writeString(file, "{id:'minecraft:horse',Passengers:[{id:'customnpcs:customnpc',Name:'骑兵',CNPCPlusInvId:'" + id + "'}]}");
        CompoundTag cavalry = ClonePreviewData.decode(ClonePreviewData.read(root.resolve("clones").toFile(), root.toFile(), 1, "测试"));
        check(cavalry != null && cavalry.getString("Name").equals("骑兵") && cavalry.getList("Armor", 10).size() == 1, "mounted NPC preview or member external equipment lost");
        check(!cavalry.contains("Passengers") && !cavalry.contains("CNPCPlusInvId"), "preview leaked complete mount/store pointer");
    }
    private static void metadata() throws Exception {
        ClassLoader loader = new ClassLoader(FinalFeaturesRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!(name.startsWith("noppes.npcs.") || name.startsWith("top.cnpcplus.script.ScriptApi") || name.startsWith("top.cnpcplus.script.ScriptLexical"))) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        ClassWriter writer = new ClassWriter(0); new ClassReader(input).accept(writer, 0);
                        if (name.equals("noppes.npcs.api.wrapper.NPCWrapper")) {
                            addApi(writer, "getAddonVisual", "()Ltop/cnpcplus/FinalFeaturesRegression$ReturnedApi;", "actual.addon.mixin.NpcMixin");
                            addApi(writer, "cnpcPlusMarker", "()I", "top.cnpcplus.mixin.NpcApi");
                            writer.visitField(Opcodes.ACC_PUBLIC, "addonField", "I", null, null).visitEnd();
                        }
                        if (name.equals("noppes.npcs.api.wrapper.EntityLivingBaseWrapper")) addApi(writer, "castFixture", "()V", "actual.magic.mixin.LivingMixin");
                        byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        Class<?> docs = loader.loadClass("top.cnpcplus.script.ScriptApiDocs");
        List<Class<?>> api = new ArrayList<>();
        for (String name : List.of("noppes.npcs.api.entity.IEntity", "noppes.npcs.api.entity.ICustomNpc", "noppes.npcs.api.entity.IPlayer", "noppes.npcs.api.event.NpcEvent")) api.add(loader.loadClass(name));
        Function<String, String> sources = name -> name.startsWith("top.cnpcplus.FinalFeaturesRegression$") || name.startsWith("actual.addon") ? "Visual [visual]"
                : name.startsWith("actual.magic") ? "Magic [magic]" : name.startsWith("top.cnpcplus") ? "CNPCplus [cnpcplus]" : "CustomNPCs [customnpcs]";
        List<?> lines = (List<?>)docs.getMethod("index", List.class, Function.class).invoke(null, api, sources);
        check(hasLine(lines, "getAddonVisual", "Visual [visual]", true), "Mixin contributor provenance lost");
        check(hasLine(lines, "cnpcPlusMarker", "CNPCplus [cnpcplus]", true), "CNPCplus category lost");
        check(hasLine(lines, "castFixture", "Magic [magic]", true), "inherited extension lost");
        check(hasLine(lines, "setModel", "Visual [visual]", true), "returned API object not documented");
        check(hasLine(lines, "addonField", "?", true), "unproven merged field mislabeled");
        check(hasLine(lines, "getName", "CustomNPCs [customnpcs]", false), "native API misclassified");
    }
    private static boolean hasLine(List<?> lines, String signature, String source, boolean extension) throws Exception {
        for (Object line : lines) if (((String)line.getClass().getMethod("signature").invoke(line)).startsWith(signature)
                && source.equals(line.getClass().getMethod("source").invoke(line)) && extension == (boolean)line.getClass().getMethod("extension").invoke(line)) return true;
        return false;
    }
    private static void addApi(ClassWriter writer, String name, String descriptor, String mixin) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        var annotation = method.visitAnnotation("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;", true);
        annotation.visit("mixin", mixin); annotation.visit("priority", 1000); annotation.visit("sessionId", "fixture"); annotation.visitEnd();
        method.visitCode(); method.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError"); method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false); method.visitInsn(Opcodes.ATHROW); method.visitMaxs(2, 1); method.visitEnd();
    }
    private static void lifecycle() throws Exception {
        String page = "top.cnpcplus.clone.client.ClonePreview";
        ClassLoader loader = new ClassLoader(FinalFeaturesRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(page)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                        for (MethodNode method : node.methods) for (var instruction : method.instructions.toArray()) {
                            if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/Minecraft") && call.name.equals("getInstance")) method.instructions.set(call, new InsnNode(Opcodes.ACONST_NULL));
                            else if (instruction instanceof FieldInsnNode field && field.owner.equals("net/minecraft/client/Minecraft") && (field.name.equals("level") || field.name.equals("player"))) {
                                var next = field.getNext(); method.instructions.set(field, new InsnNode(Opcodes.POP)); method.instructions.insertBefore(next, new InsnNode(Opcodes.ACONST_NULL));
                            }
                            else if (instruction instanceof MethodInsnNode call && call.owner.equals("top/cnpcplus/text/client/PlayerTextPreview") && call.name.equals("npc")) {
                                var next = call.getNext(); method.instructions.set(call, new InsnNode(Opcodes.POP)); method.instructions.insertBefore(next, new InsnNode(Opcodes.ACONST_NULL));
                            }
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        Class<?> type = loader.loadClass(page); Object first = type.getConstructor().newInstance(), second = type.getConstructor().newInstance();
        var select = type.getMethod("select", int.class, int.class, String.class); var receive = type.getMethod("receive", int.class, String.class, long.class, byte[].class);
        select.invoke(first, 2, 1, "A"); long a = field(type, "request").getLong(first);
        check(!(boolean)receive.invoke(first, 1, "B", a, new byte[0]), "wrong name accepted");
        check(!(boolean)receive.invoke(first, 2, "A", a, new byte[0]), "wrong tab accepted");
        check(!(boolean)receive.invoke(first, 1, "A", a + 1, new byte[0]), "wrong nonce accepted");
        select.invoke(first, 2, 1, "B"); long b = field(type, "request").getLong(first);
        check(b != a && !(boolean)receive.invoke(first, 1, "A", a, new byte[0]), "old selection accepted");
        check((boolean)receive.invoke(first, 1, "B", b, new byte[0]) && field(type, "failed").getBoolean(first), "failure not applied");
        check(!(boolean)receive.invoke(first, 1, "B", b, new byte[0]), "duplicate accepted");
        select.invoke(first, 2, 1, "C"); long c = field(type, "request").getLong(first); type.getMethod("clear").invoke(first);
        check(!(boolean)receive.invoke(first, 1, "C", c, new byte[0]), "closed page accepted reply");
        select.invoke(second, 2, 1, "C"); check(!(boolean)receive.invoke(second, 1, "C", c, new byte[0]), "reopened page accepted old reply");
        check(field(type, "npc").get(first) == null && field(type, "level").get(first) == null, "close retained world/entity");
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void previewMapping() throws Exception {
        List<List<String>> axes = new ArrayList<>();
        var names = java.util.Map.of("f_252436_", "YP", "f_252403_", "ZP", "f_252392_", "YN");
        for (String resource : List.of("noppes/npcs/client/gui/custom/components/CustomGuiEntityDisplay", "top/cnpcplus/client/gui/SafeEntityPreview")) {
            try (var input = FinalFeaturesRegression.class.getClassLoader().getResourceAsStream(resource + ".class")) {
                ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                List<String> used = new ArrayList<>();
                for (MethodNode method : node.methods) if (method.name.equals("nativeDisplay") || method.name.equals("drawEntity") && method.desc.endsWith("ZZ)V")) {
                    for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field && field.owner.equals("com/mojang/math/Axis")) used.add(names.getOrDefault(field.name, field.name));
                }
                axes.add(used);
            }
        }
        check(axes.get(0).equals(List.of("YP", "ZP", "YN")) && axes.get(1).equals(axes.get(0)), "preview axes differ from native renderer: " + axes);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void expectFailure(Action action) throws Exception { try { action.run(); } catch (IOException expected) { return; } throw new AssertionError("invalid input accepted"); }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
