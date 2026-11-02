package top.cnpcplus;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.util.NBTJsonUtil;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.clone.CloneMounts;
import top.cnpcplus.mixin.MixinServerCloneControllerOverwrite;

/** Native passenger save + production capture/validation/save and spawn rollback branches. */
public final class CloneMountRegression {
    private static sun.misc.Unsafe unsafe;
    private static boolean failMount;
    private static java.util.Map<Integer, java.util.Map<String, CompoundTag>> legacy;
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException) || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
            System.out.println("HEADLESS_ONLY: no ModLauncher event transforms");
        }
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); unsafe = (sun.misc.Unsafe)field.get(null);
        Path root = Files.createTempDirectory(Path.of("Temp/CNPCplus-final-20261101"), "mount-regression-");
        captures();
        saves(root);
        spawns();
        System.out.println("CLONE_MOUNT_REGRESSION=PASS (native save; production capture/save/rollback; factory/world fixtures, not game/Mixin smoke)");
    }
    private static void captures() throws Exception {
        Fixture horse = entity(EntityType.HORSE), rider = entity(EntityType.PIG), upper = entity(EntityType.COW), other = entity(EntityType.SHEEP);
        ride(rider, horse); ride(upper, rider); ride(other, horse);
        CompoundTag targetOnly = new CompoundTag();
        check(!rider.save(targetOnly), "native passenger save should reject rider");
        CompoundTag fromRider = CloneMounts.capture(rider), fromMount = CloneMounts.capture(horse);
        check(fromRider.equals(fromMount), "rider/mount capture differ");
        List<CompoundTag> members = CloneMounts.members(fromRider);
        check(members.size() == 4 && members.get(1).getString("id").equals("minecraft:pig")
                && members.get(2).getString("id").equals("minecraft:sheep") && members.get(3).getString("id").equals("minecraft:cow"), "multi-level member order/types lost");
        check(horse.getPassengers().equals(List.of(rider, other)) && rider.getPassengers().equals(List.of(upper)), "capture altered live mounts");
        fromRider.getList("Passengers", 10).getCompound(0).putString("Name", "edited");
        check(!CloneMounts.capture(horse).toString().contains("edited"), "snapshot aliases live source");
        rider.rejectSave = true;
        expectFailure(() -> CloneMounts.capture(horse)); rider.rejectSave = false;
        rider.payload.putString("CNPCPlusInvId", "invalid-store");
        expectFailure(() -> CloneMounts.capture(rider)); rider.payload.remove("CNPCPlusInvId");
        rider.payload.putDouble("Bad", Double.NaN); expectFailure(() -> CloneMounts.capture(rider)); rider.payload.remove("Bad");
        CompoundTag wrong = fromMount.copy(); wrong.putString("Passengers", "wrong"); expectFailure(() -> CloneMounts.members(wrong));
        CompoundTag cycle = new CompoundTag(); cycle.putString("id", "minecraft:pig"); ListTag children = new ListTag(); children.add(cycle); cycle.put("Passengers", children);
        expectFailure(() -> CloneMounts.members(cycle));
        CompoundTag large = new CompoundTag(); large.putString("id", "minecraft:pig"); large.putString("Name", "a".repeat(2 * 1024 * 1024)); expectFailure(() -> CloneMounts.members(large));
        Fixture removed = entity(EntityType.PIG); removed.discard(); expectFailure(() -> CloneMounts.capture(removed));
        Player player = (Player)unsafe.allocateInstance(net.minecraft.server.level.ServerPlayer.class);
        set(Entity.class, player, "passengers", ImmutableList.of()); ride(player, horse);
        expectFailure(() -> CloneMounts.capture(rider));
        set(Entity.class, horse, "passengers", ImmutableList.of(rider, other)); set(Entity.class, player, "vehicle", null);
        Fixture a = entity(EntityType.PIG), b = entity(EntityType.COW); ride(a, b); ride(b, a);
        expectFailure(() -> CloneMounts.capture(a));
        Fixture deep = entity(EntityType.HORSE), cursor = deep;
        for (int i = 0; i < 32; i++) { Fixture next = entity(EntityType.PIG); ride(next, cursor); cursor = next; }
        expectFailure(() -> CloneMounts.capture(deep));
    }
    private static void saves(Path root) throws Exception {
        // Actual CNPC codec bytes; only SRG names and the missing ListNBT accessor are adapted to headless Mojmap.
        ClassLoader loader = new ClassLoader(CloneMountRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("noppes.npcs.util.NBTJsonUtil") && !name.equals(MixinServerCloneControllerOverwrite.class.getName())
                        && !name.equals(SaveFixture.class.getName())) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                        if (name.equals(MixinServerCloneControllerOverwrite.class.getName())) for (MethodNode method : node.methods) if (method.name.equals("loadOldClones")) {
                            method.instructions.clear(); method.tryCatchBlocks.clear();
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "top/cnpcplus/CloneMountRegression", "legacy", "(Ljava/io/File;)Ljava/util/Map;", false));
                            method.instructions.add(new InsnNode(Opcodes.ARETURN));
                        }
                        var names = java.util.Map.ofEntries(java.util.Map.entry("m_7060_", "getId"), java.util.Map.entry("m_7264_", "getElementType"),
                                java.util.Map.entry("m_128365_", "put"), java.util.Map.entry("m_128431_", "getAllKeys"), java.util.Map.entry("m_128423_", "get"),
                                java.util.Map.entry("m_7047_", "getAsInt"), java.util.Map.entry("m_7063_", "getAsByte"), java.util.Map.entry("m_7916_", "getAsString"),
                                java.util.Map.entry("m_129297_", "valueOf"), java.util.Map.entry("m_128500_", "valueOf"), java.util.Map.entry("m_128566_", "valueOf"),
                                java.util.Map.entry("m_128266_", "valueOf"), java.util.Map.entry("m_129258_", "valueOf"), java.util.Map.entry("m_128882_", "valueOf"),
                                java.util.Map.entry("m_128679_", "valueOf"), java.util.Map.entry("m_128382_", "putByteArray"));
                        for (MethodNode method : node.methods) for (var instruction : method.instructions.toArray()) {
                            if (instruction instanceof MethodInsnNode call && call.owner.startsWith("net/minecraft/nbt/")) call.name = names.getOrDefault(call.name, call.name);
                            else if (instruction instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST && cast.desc.equals("noppes/npcs/mixin/ListNBTMixin"))
                                method.instructions.set(cast, new InsnNode(Opcodes.NOP));
                            else if (instruction instanceof MethodInsnNode call && call.owner.equals("noppes/npcs/mixin/ListNBTMixin") && call.name.equals("getList"))
                                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "top/cnpcplus/CloneMountRegression", "nativeList", "(Lnet/minecraft/nbt/ListTag;)Ljava/util/List;", false));
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        Class<?> mixin = loader.loadClass(MixinServerCloneControllerOverwrite.class.getName());
        var constructor = loader.loadClass(SaveFixture.class.getName()).getDeclaredConstructor(); constructor.setAccessible(true);
        Object fixture = constructor.newInstance(); Field directory = fixture.getClass().getDeclaredField("root"); directory.setAccessible(true);
        directory.set(fixture, Files.createDirectories(root.resolve("clones")).toFile());
        Field cache = mixin.getDeclaredField("cloneCache"); cache.setAccessible(true);
        var data = new HashMap<String, CompoundTag>(); cache.set(fixture, data);
        var save = mixin.getDeclaredMethod("cnpcplus$verifiedSave", int.class, String.class, CompoundTag.class, CallbackInfo.class); save.setAccessible(true);
        var read = loader.loadClass(NBTJsonUtil.class.getName()).getMethod("LoadFile", java.io.File.class);
        Field lastLoaded = mixin.getField("lastLoaded");
        CompoundTag tag = new CompoundTag(); tag.putString("id", "minecraft:horse"); tag.putString("Name", "旧骑兵");
        Path file = root.resolve("clones/1/骑兵.json");
        save.invoke(fixture, 1, "骑兵", tag, new CallbackInfo("saveClone", true));
        check(read.invoke(null, file.toFile()).equals(tag) && data.get("1|骑兵").equals(tag), "first verified save failed");
        tag.putString("Name", "新骑兵"); save.invoke(fixture, 1, "骑兵", tag, new CallbackInfo("saveClone", true));
        long changed = lastLoaded.getLong(fixture);
        check(read.invoke(null, file.toFile()).equals(tag) && data.get("1|骑兵").equals(tag), "overwrite left stale cache");
        tag.putString("Name", "not-saved"); check(!data.get("1|骑兵").equals(tag), "cache aliases caller");
        byte[] before = Files.readAllBytes(file);
        save.invoke(fixture, 1, "../骑兵", tag, new CallbackInfo("saveClone", true));
        check(java.util.Arrays.equals(before, Files.readAllBytes(file)) && lastLoaded.getLong(fixture) == changed, "invalid name changed old clone");
        Path blocked = Files.createDirectories(root.resolve("clones/1/blocked.json")); Files.writeString(blocked.resolve("keep.txt"), "keep");
        data.put("1|blocked", data.get("1|骑兵").copy()); CompoundTag old = data.get("1|blocked").copy();
        save.invoke(fixture, 1, "blocked", tag, new CallbackInfo("saveClone", true));
        check(Files.readString(blocked.resolve("keep.txt")).equals("keep") && data.get("1|blocked").equals(old) && lastLoaded.getLong(fixture) == changed, "move failure deleted old file/cache");
        CompoundTag arrays = new CompoundTag(); arrays.putLongArray("Longs", new long[]{128L, 65537L, Long.MAX_VALUE});
        Path arrayFile = root.resolve("codec-arrays.json");
        loader.loadClass(NBTJsonUtil.class.getName()).getMethod("SaveFile", java.io.File.class, CompoundTag.class).invoke(null, arrayFile.toFile(), arrays);
        check(!read.invoke(null, arrayFile.toFile()).equals(arrays), "native long-array truncation not reproduced");
        check(TagParser.parseTag(Files.readString(arrayFile)).equals(arrays), "native writer is not SNBT-compatible");
        System.out.println("NATIVE_CODEC_LONG_ARRAY_TRUNCATION=REPRODUCED; vanilla TagParser preserves the actual writer output");
        CompoundTag complete = CloneMounts.capture(entity(EntityType.HORSE));
        CompoundTag member = CloneMounts.capture(entity(EntityType.PIG));
        member.put("Arrays", arrays); member.putIntArray("Ints", new int[]{-1, 65537}); member.putByteArray("Bytes", new byte[]{-128, 127});
        member.putLongArray("EmptyLongs", new long[0]); member.putIntArray("EmptyInts", new int[0]);
        ListTag numeric = new ListTag(); numeric.add(IntTag.valueOf(128)); numeric.add(IntTag.valueOf(65537)); member.put("IntList", numeric);
        ListTag tree = new ListTag(); tree.add(member); complete.put("Passengers", tree);
        save.invoke(fixture, 1, "完整骑兵", complete, new CallbackInfo("saveClone", true));
        Path completeFile = root.resolve("clones/1/完整骑兵.json");
        var cloneRead = mixin.getDeclaredMethod("cnpcplus$readWithoutTruncation", java.io.File.class); cloneRead.setAccessible(true);
        check(cloneRead.invoke(fixture, completeFile.toFile()).equals(complete) && data.get("1|完整骑兵").equals(complete), "complete clone tree/array types changed on save/reload");
        try (var files = Files.list(completeFile.getParent())) { check(files.noneMatch(path -> path.toString().endsWith(".tmp")), "save failure leaked temp files"); }
        // Production migration and verified writer/IO; only decoding the old .dat is supplied as a test boundary.
        Path source = root.resolve("clonednpcs.dat"), archive = root.resolve("clonednpcs.dat_old");
        Files.writeString(source, "legacy-source"); Files.writeString(archive, "old-backup");
        var imported = new java.util.LinkedHashMap<String, CompoundTag>(); imported.put("骑兵", complete.copy());
        imported.put("迁移骑兵", complete.copy()); imported.put("blocked", complete.copy()); legacy = java.util.Map.of(1, imported);
        var migrate = mixin.getDeclaredMethod("cnpcplus$migrateWithoutDeletingSource", CallbackInfo.class); migrate.setAccessible(true);
        migrate.invoke(fixture, new CallbackInfo("loadClones", true));
        check(Files.readString(source).equals("legacy-source") && java.util.Arrays.equals(before, Files.readAllBytes(file))
                && Files.isRegularFile(root.resolve("clones/1/迁移骑兵.json")) && Files.readString(archive).equals("old-backup"), "failed migration deleted source/backup or replaced existing clone");
        CompoundTag edited = complete.copy(); edited.putString("Name", "new-edit"); save.invoke(fixture, 1, "迁移骑兵", edited, new CallbackInfo("saveClone", true));
        Files.delete(blocked.resolve("keep.txt")); Files.delete(blocked);
        migrate.invoke(fixture, new CallbackInfo("loadClones", true));
        check(!Files.exists(source) && Files.readString(archive).equals("old-backup")
                && cloneRead.invoke(fixture, root.resolve("clones/1/迁移骑兵.json").toFile()).equals(edited), "migration retry lost edit or old backup");
        try (var files = Files.list(root)) {
            var backups = files.filter(path -> path.getFileName().toString().startsWith("clonednpcs-") && path.toString().endsWith(".dat_old")).toList();
            check(backups.size() == 1 && Files.readString(backups.get(0)).equals("legacy-source"), "successful migration did not archive source");
        }
        Files.writeString(source, "decode-failure"); legacy = null;
        migrate.invoke(fixture, new CallbackInfo("loadClones", true));
        check(Files.readString(source).equals("decode-failure"), "decode failure deleted legacy source");
    }
    public static java.util.Map<Integer, java.util.Map<String, CompoundTag>> legacy(java.io.File file) throws IOException {
        if (legacy == null) throw new IOException("test-only legacy decode failure");
        return legacy;
    }
    public static List<Tag> nativeList(ListTag list) { return list; }
    private static void spawns() throws Exception {
        // Replace only real entity factories, clone-controller singleton and world thread probe.
        String name = "top.cnpcplus.clone.CloneMounts";
        ClassLoader loader = new ClassLoader(CloneMountRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String type, boolean resolve) throws ClassNotFoundException {
                if (!type.equals(name) && !type.equals("top.cnpcplus.clone.ClonePreviewData")) return super.loadClass(type, resolve);
                synchronized (getClassLoadingLock(type)) {
                    Class<?> loaded = findLoadedClass(type);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(type.replace('.', '/') + ".class")) {
                        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                        for (MethodNode method : node.methods) for (var instruction : method.instructions.toArray()) {
                            if (instruction instanceof FieldInsnNode f && f.owner.equals("noppes/npcs/controllers/ServerCloneController") && f.name.equals("Instance")) method.instructions.set(f, new InsnNode(Opcodes.ACONST_NULL));
                            else if (instruction instanceof MethodInsnNode call && call.owner.equals("noppes/npcs/controllers/ServerCloneController") && call.name.equals("cleanTags")) {
                                call.setOpcode(Opcodes.INVOKESTATIC); call.owner = "top/cnpcplus/CloneMountRegression"; call.name = "clean"; call.desc = "(Lnoppes/npcs/controllers/ServerCloneController;Lnet/minecraft/nbt/CompoundTag;)V";
                            } else if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/world/entity/EntityType") && call.name.equals("loadEntityRecursive")) call.owner = "top/cnpcplus/CloneMountRegression";
                            else if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/level/ServerLevel") && call.name.equals("getServer")) {
                                var next = call.getNext(); method.instructions.set(call, new InsnNode(Opcodes.POP)); method.instructions.insertBefore(next, new InsnNode(Opcodes.ACONST_NULL));
                            } else if (instruction instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/MinecraftServer") && call.name.equals("isSameThread")) {
                                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "top/cnpcplus/CloneMountRegression", "sameThread", "(Lnet/minecraft/server/MinecraftServer;)Z", false));
                            }
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray(); loaded = defineClass(type, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(type, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        var spawn = loader.loadClass(name).getMethod("spawn", CompoundTag.class, double.class, double.class, double.class, Level.class, Entity.class);
        World world = (World)unsafe.allocateInstance(World.class); world.added = new ArrayList<>();
        CompoundTag saved = new CompoundTag(); saved.putString("id", "minecraft:horse"); saved.putUUID("UUID", UUID.randomUUID());
        ListTag passengers = new ListTag(); CompoundTag rider = new CompoundTag(); rider.putString("id", "minecraft:pig"); rider.putUUID("UUID", UUID.randomUUID()); passengers.add(rider); saved.put("Passengers", passengers);
        CompoundTag before = saved.copy();
        Entity root = (Entity)spawn.invoke(null, saved, 1d, 2d, 3d, world, null);
        check(root != null && world.added.size() == 2 && root.getPassengers().size() == 1 && saved.equals(before), "complete spawn/source independence failed");
        UUID first = root.getUUID(); world.added.clear(); root = (Entity)spawn.invoke(null, saved, 1d, 2d, 3d, world, null);
        check(root != null && !first.equals(root.getUUID()) && root.getSelfAndPassengers().map(Entity::getUUID).distinct().count() == 2, "repeat/member spawn reused UUID");
        world.added.clear(); world.rejectAt = 2;
        check(spawn.invoke(null, saved, 1d, 2d, 3d, world, null) == null && world.added.stream().allMatch(Entity::isRemoved), "world insertion failure left partial mount");
        world.rejectAt = 0; world.added.clear(); failMount = true;
        check(spawn.invoke(null, saved, 1d, 2d, 3d, world, null) == null && world.added.isEmpty(), "cancelled mount accepted"); failMount = false;
        rider.putString("id", "missing:entity"); world.added.clear();
        check(spawn.invoke(null, saved, 1d, 2d, 3d, world, null) == null && world.added.isEmpty(), "missing passenger silently skipped");
        rider.putString("id", "minecraft:pig");
        check(spawn.invoke(null, saved, Double.NaN, 2d, 3d, world, null) == null && world.added.isEmpty(), "invalid spawn coordinates accepted");
        CompoundTag legacy = new CompoundTag(); legacy.putString("id", "minecraft:pig");
        check(spawn.invoke(null, legacy, 1d, 2d, 3d, world, null) != null && world.added.size() == 1, "old single-entity clone regressed");
    }
    public static boolean sameThread(net.minecraft.server.MinecraftServer ignored) { return true; }
    public static void clean(noppes.npcs.controllers.ServerCloneController ignored, CompoundTag tag) {
        tag.remove("UUID"); tag.remove("Pos");
        for (Tag child : tag.getList("Passengers", 10)) clean(null, (CompoundTag)child);
    }
    public static Entity loadEntityRecursive(CompoundTag tag, Level world, Function<Entity, Entity> prepare) {
        if (tag.getString("id").startsWith("missing:")) return null;
        try {
            Fixture root = entity(EntityType.byString(tag.getString("id")).orElseThrow()); set(Entity.class, root, "level", world);
            prepare.apply(root);
            for (Tag child : tag.getList("Passengers", 10)) { Entity entity = loadEntityRecursive((CompoundTag)child, world, prepare); if (entity != null) entity.startRiding(root, true); }
            return root;
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static Fixture entity(EntityType<?> type) throws ReflectiveOperationException {
        Fixture entity = (Fixture)unsafe.allocateInstance(Fixture.class); entity.payload = new CompoundTag();
        set(Entity.class, entity, "type", type); set(Entity.class, entity, "passengers", ImmutableList.of()); set(Entity.class, entity, "uuid", UUID.randomUUID());
        return entity;
    }
    private static void ride(Entity child, Entity parent) throws ReflectiveOperationException {
        List<Entity> list = new ArrayList<>(parent.getPassengers()); list.add(child); set(Entity.class, parent, "passengers", ImmutableList.copyOf(list)); set(Entity.class, child, "vehicle", parent);
    }
    private static void set(Class<?> type, Object object, String name, Object value) throws ReflectiveOperationException { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    public static final class Fixture extends Entity {
        CompoundTag payload; boolean rejectSave;
        private Fixture() { super(null, null); }
        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag tag) {}
        @Override protected void addAdditionalSaveData(CompoundTag tag) {}
        @Override public boolean saveAsPassenger(CompoundTag tag) { return !rejectSave && super.saveAsPassenger(tag); }
        @Override public CompoundTag saveWithoutId(CompoundTag tag) {
            for (String key : payload.getAllKeys()) tag.put(key, payload.get(key).copy()); tag.putUUID("UUID", getUUID());
            ListTag passengers = new ListTag(); for (Entity entity : getPassengers()) { CompoundTag child = new CompoundTag(); if (entity.saveAsPassenger(child)) passengers.add(child); }
            if (!passengers.isEmpty()) tag.put("Passengers", passengers); return tag;
        }
        @Override public void moveTo(double x, double y, double z, float yaw, float pitch) {}
        @Override public boolean startRiding(Entity parent, boolean force) { if (failMount) return false; try { ride(this, parent); return true; } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }
        @Override public void remove(RemovalReason reason) { try { set(Entity.class, this, "removalReason", reason); } catch (ReflectiveOperationException e) { throw new AssertionError(e); } }
    }
    public static final class World extends ServerLevel {
        List<Entity> added; int rejectAt;
        private World() { super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null); }
        @Override public boolean addFreshEntity(Entity entity) { added.add(entity); return rejectAt == 0 || added.size() != rejectAt; }
    }
    private static final class SaveFixture extends MixinServerCloneControllerOverwrite {
        java.io.File root;
        @Override public java.io.File getDir() { return root; }
        @Override public void saveClone(int tab, String name, CompoundTag tag) {
            try {
                var save = getClass().getSuperclass().getDeclaredMethod("cnpcplus$verifiedSave", int.class, String.class, CompoundTag.class, CallbackInfo.class);
                save.setAccessible(true); save.invoke(this, tab, name, tag, new CallbackInfo("saveClone", true));
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void expectFailure(Action action) throws Exception { try { action.run(); } catch (IOException expected) { return; } throw new AssertionError("invalid clone accepted"); }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
}
