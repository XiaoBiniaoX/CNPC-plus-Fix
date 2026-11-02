package top.cnpcplus;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import top.cnpcplus.category.CategoryDirFix;
import top.cnpcplus.roles.TradeData;

/** Real file IO; one serializer fault injection models a write failing after opening its stream. */
public final class SaveBoundaryRegression {
    private static boolean failWrite;
    public static void write(CompoundTag tag, OutputStream out) throws IOException {
        if (failWrite) { out.write(new byte[]{1, 2, 3}); throw new IOException("injected partial serialization"); }
        NbtIo.writeCompressed(tag, out);
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException) || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
        }
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        Path root = Files.createTempDirectory(Path.of("Temp/CNPCplus-final-20261101"), "save-boundary-");
        ClassLoader loader = new ClassLoader(SaveBoundaryRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals("top.cnpcplus.villagertrade.TradeDataStore")) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                    for (MethodNode method : node.methods) for (var instruction : method.instructions.toArray()) {
                        if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/nbt/NbtIo") && call.name.equals("writeCompressed")) {
                            call.owner = "top/cnpcplus/SaveBoundaryRegression"; call.name = "write";
                        }
                    }
                    ClassWriter writer = new ClassWriter(0); node.accept(writer);
                    byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        };
        Class<?> store = loader.loadClass("top.cnpcplus.villagertrade.TradeDataStore");
        var path = store.getMethod("setStoragePath", File.class);
        var save = store.getMethod("save", int.class, List.class);
        var load = store.getMethod("load", int.class);
        Path target = root.resolve("trades/villager_trades.dat");
        path.invoke(null, target.toFile());
        TradeData trade = new TradeData(); trade.uses = 1; trade.limitResetAt = Long.MAX_VALUE;
        save.invoke(null, 7, List.of(trade));
        byte[] original = Files.readAllBytes(target);
        trade.uses = 2; failWrite = true;
        try { save.invoke(null, 7, List.of(trade)); } finally { failWrite = false; }
        check(((List<TradeData>)load.invoke(null, 7)).get(0).uses == 2, "runtime consumed uses rolled back");
        check(Arrays.equals(original, Files.readAllBytes(target)) != baseline, "partial serialization old-file contract");
        System.out.println(baseline ? "REPRODUCED: partial serialization truncates old trade library" : "PASS: partial serialization preserves old library and runtime uses");
        if (!baseline) {
            try (var held = Files.newByteChannel(target, Set.of(StandardOpenOption.READ, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE))) {
                save.invoke(null, 7, List.of(trade));
                check(Arrays.equals(original, Files.readAllBytes(target)), "locked replacement lost old trade library");
            }
            save.invoke(null, 7, List.of(trade));
            check(NbtIo.readCompressed(target.toFile()).getCompound("7").getList("Trades", 10).getCompound(0).getInt("Uses") == 2, "retry failed");
            store.getMethod("clear").invoke(null);
            check(!(boolean)store.getMethod("isReady").invoke(null), "stop retained ready path");
            store.getMethod("reserve", int.class).invoke(null, 1);
            path.invoke(null, target.toFile());
            check(((List<TradeData>)load.invoke(null, 7)).get(0).uses == 2, "successful disk reload failed");
            check((int)store.getMethod("nextId").invoke(null) == 2, "start discarded IDs reserved by NPC load");
        }
        Path empty = root.resolve("empty/villager_trades.dat"); path.invoke(null, empty.toFile());
        check(((List<?>)load.invoke(null, 7)).isEmpty() != baseline, "new empty store inherited server data");
        check(((List<?>)store.getMethod("loadClient", int.class).invoke(null, 7)).isEmpty() != baseline, "new store inherited client cache");
        System.out.println(baseline ? "REPRODUCED: changing to absent library keeps stale server/client data" : "PASS: new path clears data/cache, stop clears readiness, load reservations retained");
        if (!baseline) {
            Files.createDirectories(empty.getParent()); Files.write(empty, new byte[]{1, 2, 3});
            path.invoke(null, empty.toFile()); save.invoke(null, 8, List.of(trade));
            check(Arrays.equals(Files.readAllBytes(empty), new byte[]{1, 2, 3}), "unreadable library overwritten");
            CompoundTag validEntry = new CompoundTag(); ListTag validTrades = new ListTag(); validTrades.add(trade.save(new CompoundTag())); validEntry.put("Trades", validTrades);
            CompoundTag invalid = new CompoundTag(); invalid.put("1", validEntry); invalid.put("bad-id", validEntry.copy());
            NbtIo.writeCompressed(invalid, empty.toFile()); byte[] invalidBytes = Files.readAllBytes(empty);
            path.invoke(null, empty.toFile());
            check(((List<?>)load.invoke(null, 1)).isEmpty(), "malformed load partially committed");
            save.invoke(null, 8, List.of(trade)); check(Arrays.equals(invalidBytes, Files.readAllBytes(empty)), "malformed library overwritten");
            invalid.remove("bad-id"); invalid.putString("2", "not a trade list"); NbtIo.writeCompressed(invalid, empty.toFile()); invalidBytes = Files.readAllBytes(empty);
            path.invoke(null, empty.toFile()); check(((List<?>)load.invoke(null, 1)).isEmpty(), "wrong-type load partially committed");
            save.invoke(null, 8, List.of(trade)); check(Arrays.equals(invalidBytes, Files.readAllBytes(empty)), "wrong-type library overwritten");
            System.out.println("PASS: corrupt/non-numeric library rejected without partial commit or overwrite");
        }
        Path categories = Files.createDirectories(root.resolve("categories"));
        Path old = Files.createDirectories(categories.resolve("old")), fresh = categories.resolve("new");
        Files.writeString(old.resolve("1.json"), "first"); Files.writeString(old.resolve("2.json"), "second");
        Object controller = new Object(); CategoryDirFix.remember(controller, 1, "old");
        try (var held = Files.newByteChannel(old.resolve("2.json"), Set.of(StandardOpenOption.READ, com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE))) {
            if (baseline) {
                var fallback = CategoryDirFix.class.getDeclaredMethod("moveContents", File.class, File.class); fallback.setAccessible(true);
                check(!(boolean)fallback.invoke(null, old.toFile(), fresh.toFile()), "locked fallback unexpectedly succeeded");
                check(Files.exists(fresh.resolve("1.json")) && Files.exists(old.resolve("2.json")), "partial category not reproduced");
                System.out.println("REPRODUCED: failed fallback splits category files across old/new directories");
            } else {
                check(CategoryDirFix.syncDir(controller, categories.toFile(), 1, "new").equals("old"), "locked category title not rolled back");
                check(Files.readString(old.resolve("1.json")).equals("first") && Files.readString(old.resolve("2.json")).equals("second") && !Files.exists(fresh), "failed category rename split files");
            }
        }
        if (!baseline) {
            check(CategoryDirFix.syncDir(controller, categories.toFile(), 1, "new").equals("new"), "category retry failed");
            check(!Files.exists(old) && Files.readString(fresh.resolve("1.json")).equals("first") && Files.readString(fresh.resolve("2.json")).equals("second"), "category retry lost content");
            Files.createDirectories(categories.resolve("occupied")); Files.writeString(categories.resolve("occupied/9.json"), "keep");
            check(!CategoryDirFix.renameDir(categories.toFile(), "new", "occupied") && Files.readString(categories.resolve("occupied/9.json")).equals("keep"), "occupied category merged");
            System.out.println("PASS: real locked category keeps title/all files; retry moves whole directory; occupied target refused");
        }
        System.out.println((baseline ? "SAVE_BOUNDARY_BASELINE=REPRODUCED" : "SAVE_BOUNDARY_REGRESSION=PASS") + " (serializer fault/real IO/helper only; not game/Mixin smoke)");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
