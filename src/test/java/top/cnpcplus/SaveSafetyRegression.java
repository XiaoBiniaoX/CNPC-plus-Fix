package top.cnpcplus;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import top.cnpcplus.persist.FileReplacement;

/** Production writers and real locked-file failures; only world-directory lookup is replaced. */
public final class SaveSafetyRegression {
    public static File directory;
    public static File worldDirectory() { return directory; }
    public static List<Tag> nativeList(ListTag list) { return list; }

    public static void main(String[] args) throws Exception {
        try (var input = SaveSafetyRegression.class.getClassLoader().getResourceAsStream("top/cnpcplus/mixin/MixinRoleTraderMarketCache.class")) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
            AnnotationNode mixin = node.invisibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            check(mixin.values.contains("priority") && (int)mixin.values.get(mixin.values.indexOf("priority") + 1) == 900,
                    "market safety injection can be cancelled by default-priority legacy caches");
        }
        try (var input = SaveSafetyRegression.class.getClassLoader().getResourceAsStream("org/spongepowered/asm/mixin/transformer/MixinInfo.class")) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            MethodNode compare = node.methods.stream().filter(m -> m.name.equals("compareTo") && m.desc.equals("(Lorg/spongepowered/asm/mixin/transformer/MixinInfo;)I")).findFirst().orElseThrow();
            var code = Arrays.stream(compare.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
            int end = code.size();
            check(code.get(end - 6) instanceof VarInsnNode left && left.getOpcode() == Opcodes.ALOAD && left.var == 0
                    && code.get(end - 5) instanceof FieldInsnNode a && a.name.equals("priority")
                    && code.get(end - 4) instanceof VarInsnNode right && right.getOpcode() == Opcodes.ALOAD && right.var == 1
                    && code.get(end - 3) instanceof FieldInsnNode b && b.name.equals("priority")
                    && code.get(end - 2).getOpcode() == Opcodes.ISUB && code.get(end - 1).getOpcode() == Opcodes.IRETURN,
                    "actual Mixin comparator is not this.priority - other.priority; recheck entry order");
        }
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException) || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
        }
        Path root = Files.createTempDirectory(Path.of("Temp/CNPCplus-final-20261101"), "save-safety-");
        ClassLoader loader = new ClassLoader(SaveSafetyRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                boolean own = name.startsWith("noppes.npcs.util.NBTJsonUtil") || name.equals("top.cnpcplus.perf.MarketCache")
                    || name.equals("top.cnpcplus.transport.TransportImageIO") || name.endsWith("RecipeRegistry");
                if (!own) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                        for (MethodNode method : node.methods) for (var instruction : method.instructions.toArray()) {
                            if (instruction instanceof FieldInsnNode field && field.owner.equals("noppes/npcs/CustomNpcs") && field.name.equals("Dir")) {
                                field.owner = "top/cnpcplus/SaveSafetyRegression"; field.name = "directory";
                            } else if (instruction instanceof MethodInsnNode call && call.owner.equals("noppes/npcs/CustomNpcs") && call.name.equals("getLevelSaveDirectory")) {
                                call.owner = "top/cnpcplus/SaveSafetyRegression"; call.name = "worldDirectory";
                            } else if (instruction instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals("noppes/npcs/mixin/ListNBTMixin")) {
                                method.instructions.set(cast, new InsnNode(Opcodes.NOP));
                            } else if (instruction instanceof MethodInsnNode call && call.owner.equals("noppes/npcs/mixin/ListNBTMixin") && call.name.equals("getList")) {
                                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "top/cnpcplus/SaveSafetyRegression", "nativeList", "(Lnet/minecraft/nbt/ListTag;)Ljava/util/List;", false));
                            }
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
                        byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Path old = root.resolve("old.dat"), missing = root.resolve("missing.tmp"); Files.writeString(old, "keep");
        try { FileReplacement.replace(missing, old); throw new AssertionError("missing temporary accepted"); }
        catch (java.io.IOException expected) { check(Files.readString(old).equals("keep"), "replacement deleted old file"); }
        for (String kind : List.of("anvil", "smithing", "drop", "smelting")) registry(loader, root, kind);
        market(loader, root); image(loader, root);
        System.out.println("SAVE_SAFETY_REGRESSION=PASS (real IO/locked targets/old bytes/cache/retry/async captured directory; not game packet smoke)");
    }

    private static void registry(ClassLoader loader, Path root, String kind) throws Exception {
        String prefix = kind.substring(0, 1).toUpperCase(Locale.ROOT) + kind.substring(1);
        Class<?> registry = loader.loadClass("top.cnpcplus." + kind + "." + prefix + "RecipeRegistry");
        Field cache = registry.getDeclaredField("cache"); cache.setAccessible(true);
        cache.set(null, new LinkedHashMap<>());
        directory = Files.createDirectories(root.resolve(kind)).toFile();
        Path target = directory.toPath().resolve(kind + "_recipes.dat");
        CompoundTag before = new CompoundTag(); before.putString("State", "old"); NbtIo.writeCompressed(before, target.toFile());
        CompoundTag after = new CompoundTag(); after.put("Data", new ListTag());
        var write = registry.getDeclaredMethod(kind.equals("smelting") ? "writeDisk" : "writeRoot",
            kind.equals("smelting") ? new Class<?>[0] : new Class<?>[]{CompoundTag.class, File.class});
        write.setAccessible(true);
        byte[] old = Files.readAllBytes(target);
        try (var held = Files.newByteChannel(target, Set.of(StandardOpenOption.READ, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE))) {
            if (kind.equals("smelting")) write.invoke(null); else write.invoke(null, after, target.toFile());
            check(Arrays.equals(old, Files.readAllBytes(target)), kind + " failure lost old bytes");
        }
        if (kind.equals("smelting")) write.invoke(null); else write.invoke(null, after, target.toFile());
        check(NbtIo.readCompressed(target.toFile()).equals(after), kind + " successful retry did not replace");
        if (!kind.equals("smelting")) {
            Field field = registry.getDeclaredField("DISK_EXEC"); field.setAccessible(true);
            ExecutorService executor = (ExecutorService)field.get(null);
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            executor.execute(() -> { entered.countDown(); try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } });
            check(entered.await(5, TimeUnit.SECONDS), "worker not blocked");
            NbtIo.writeCompressed(before, target.toFile());
            var queue = registry.getDeclaredMethod("writeDisk"); queue.setAccessible(true); queue.invoke(null);
            directory = Files.createDirectories(root.resolve(kind + "-next-world")).toFile();
            Path next = directory.toPath().resolve(kind + "_recipes.dat"); NbtIo.writeCompressed(before, next.toFile());
            release.countDown(); registry.getMethod("awaitDiskPending").invoke(null);
            check(NbtIo.readCompressed(target.toFile()).equals(after) && NbtIo.readCompressed(next.toFile()).equals(before), kind + " worker used changed world directory");
        }
        System.out.println("PASS: " + kind + " old bytes retained on locked IO, success/retry/readback" + (kind.equals("smelting") ? "" : "/captured target"));
    }

    private static void market(ClassLoader loader, Path root) throws Exception {
        Class<?> market = loader.loadClass("top.cnpcplus.perf.MarketCache");
        directory = Files.createDirectories(root.resolve("market")).toFile();
        var store = market.getMethod("store", String.class, CompoundTag.class);
        var load = market.getMethod("load", String.class);
        CompoundTag original = new CompoundTag(); original.putString("Name", "original");
        original.putLongArray("Longs", new long[]{128L, 65537L, Long.MAX_VALUE});
        ListTag nested = new ListTag(); CompoundTag caps = new CompoundTag(); caps.putString("ForgeCaps", "complete"); nested.add(caps); original.put("Pages", nested);
        store.invoke(null, "shop", original); Path target = directory.toPath().resolve("markets/shop.json");
        check(load.invoke(null, "shop").equals(original), "market first save/cache failed");
        market.getMethod("clear").invoke(null); check(load.invoke(null, "shop").equals(original), "market long array disk read truncated");
        byte[] old = Files.readAllBytes(target);
        CompoundTag edited = original.copy(); edited.putString("Name", "edited");
        try (var held = Files.newByteChannel(target, Set.of(StandardOpenOption.READ, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE))) {
            store.invoke(null, "shop", edited);
            check(Arrays.equals(old, Files.readAllBytes(target)) && load.invoke(null, "shop").equals(original), "market failure changed old file/cache");
        }
        store.invoke(null, "shop", edited); check(load.invoke(null, "shop").equals(edited), "market retry stale");
        edited.putString("Name", "caller mutation"); check(!load.invoke(null, "shop").equals(edited), "market aliases caller");
        CompoundTag returned = (CompoundTag)load.invoke(null, "shop"); returned.putString("Name", "returned mutation");
        market.getMethod("clear").invoke(null); check(((CompoundTag)load.invoke(null, "shop")).getString("Name").equals("edited"), "market disk/cache lost independence");
        System.out.println("PASS: market full NBT/long arrays, locked failure keeps bytes/cache, retry and independent reload");
    }

    private static void image(ClassLoader loader, Path root) throws Exception {
        Class<?> image = loader.loadClass("top.cnpcplus.transport.TransportImageIO");
        directory = Files.createDirectories(root.resolve("image")).toFile();
        var write = image.getMethod("write", String.class, byte[].class);
        byte[] old = {1, 2, 3}, fresh = {4, 5, 6};
        check((boolean)write.invoke(null, "image", old), "image first save failed");
        Path target = ((File)image.getMethod("fileFor", String.class).invoke(null, "image")).toPath();
        try (var held = Files.newByteChannel(target, Set.of(StandardOpenOption.READ, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE))) {
            check(!(boolean)write.invoke(null, "image", fresh) && Arrays.equals(old, Files.readAllBytes(target)), "image failure claimed success/deleted old image");
        }
        check((boolean)write.invoke(null, "image", fresh) && Arrays.equals(fresh, Files.readAllBytes(target)), "image retry did not replace");
        System.out.println("PASS: image locked failure returns false/keeps old bytes, successful retry");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
