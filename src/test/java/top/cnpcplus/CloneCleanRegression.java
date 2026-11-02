package top.cnpcplus;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Actual mapped native cleanTags plus test-assembled production handlers; only world/config access is replaced. */
public final class CloneCleanRegression {
    private static File world;
    private static final String CONTROLLER = "noppes/npcs/controllers/ServerCloneController";
    private static final String STORE = "top/cnpcplus/persist/NpcDataExternalStore";
    private static final String[] STORES = {"SPAWNER", "INVENTORY", "SCRIPTS", "ADVANCED", "MODEL"};
    private static final String[] IDS = {"CNPCPlusSpawnerId", "CNPCPlusInvId", "CNPCPlusScriptsId", "CNPCPlusAdvancedId", "CNPCPlusModelId"};

    public static void main(String[] args) throws Exception {
        world = Files.createTempDirectory(Path.of("Temp/CNPCplus-final-20261101"), "clean-regression-").toFile();
        ClassLoader loader = new ClassLoader(CloneCleanRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                String path = name.replace('.', '/');
                if (!path.equals(CONTROLLER) && !path.equals(STORE) && !path.startsWith(STORE + "$")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(path + ".class")) {
                        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                        if (path.equals(CONTROLLER)) {
                            String mixinName = "top/cnpcplus/mixin/MixinClonePortableData";
                            ClassNode mixin = new ClassNode();
                            try (var source = getParent().getResourceAsStream(mixinName + ".class")) { new ClassReader(source).accept(mixin, 0); }
                            for (MethodNode method : mixin.methods) if (method.name.startsWith("cnpcplus$")) node.methods.add(method);
                            MethodNode clean = node.methods.stream().filter(m -> m.name.equals("cleanTags")).findFirst().orElseThrow();
                            InsnList head = hook("cnpcplus$materializeBeforeUuidRemoval"); clean.instructions.insert(head);
                            for (var instruction : clean.instructions.toArray()) {
                                if (instruction instanceof FieldInsnNode f && f.owner.equals("noppes/npcs/CustomNpcs") && f.name.equals("FixUpdateFromPre_1_12"))
                                    clean.instructions.set(f, new InsnNode(Opcodes.ICONST_0));
                                if (instruction.getOpcode() == Opcodes.RETURN) clean.instructions.insertBefore(instruction, hook("cnpcplus$cleanPassengers"));
                            }
                        } else if (path.equals(STORE)) {
                            for (MethodNode method : node.methods) for (var instruction : method.instructions.toArray())
                                if (instruction instanceof MethodInsnNode call && call.owner.equals("noppes/npcs/CustomNpcs") && call.name.equals("getLevelSaveDirectory"))
                                    call.owner = "top/cnpcplus/CloneCleanRegression";
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        Class<?> storeType = loader.loadClass(STORE.replace('/', '.'));
        var save = storeType.getDeclaredMethod("saveNpc", Object.class, UUID.class, CompoundTag.class, File.class); save.setAccessible(true);
        var load = storeType.getDeclaredMethod("prepareLoad", Object.class, UUID.class, CompoundTag.class, File.class); load.setAccessible(true);
        var directory = storeType.getDeclaredMethod("directory"); directory.setAccessible(true);
        List<Object> stores = new ArrayList<>(); List<File> directories = new ArrayList<>();
        for (String name : STORES) { Object store = storeType.getField(name).get(null); stores.add(store); directories.add((File)directory.invoke(store)); }
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); var unsafe = (sun.misc.Unsafe)field.get(null);
        Class<?> controllerType = loader.loadClass(CONTROLLER.replace('/', '.'));
        Object controller = unsafe.allocateInstance(controllerType);
        var clean = controllerType.getMethod("cleanTags", CompoundTag.class);
        CompoundTag horse = member("minecraft:horse", "horse"), npc = member("customnpcs.customnpc", "npc"), upper = member("minecraft:pig", "upper");
        attach(horse, npc); attach(npc, upper);
        List<CompoundTag> originals = List.of(horse, npc, upper); List<CompoundTag> full = new ArrayList<>();
        List<Path> files = new ArrayList<>(); List<byte[]> bytes = new ArrayList<>();
        for (CompoundTag tag : originals) {
            full.add(tag.copy()); UUID owner = tag.getUUID("UUID");
            for (int i = 0; i < stores.size(); i++) {
                save.invoke(stores.get(i), new Object(), owner, tag, directories.get(i));
                check(tag.contains(IDS[i]), "store fixture did not externalize " + IDS[i]);
                Path file = directories.get(i).toPath().resolve(tag.getString(IDS[i]) + ".dat"); files.add(file); bytes.add(Files.readAllBytes(file));
            }
        }
        CompoundTag external = horse.copy(), portable = horse.copy(); clean.invoke(controller, portable);
        List<CompoundTag> members = List.of(portable, portable.getList("Passengers", 10).getCompound(0), portable.getList("Passengers", 10).getCompound(0).getList("Passengers", 10).getCompound(0));
        for (int j = 0; j < members.size(); j++) {
            CompoundTag tag = members.get(j);
            for (String key : List.of("UUID", "UUIDMost", "UUIDLeast", "Pos", "Riding", "StartPos", "StartPosNew", "MovingPathNew")) check(!tag.contains(key), "native identity/path remained " + key);
            for (String id : IDS) check(!tag.contains(id), "external reference remained " + id);
            check(tag.getString("Name").equals(full.get(j).getString("Name")) && tag.getList("Weapons", 10).equals(full.get(j).getList("Weapons", 10)), "member equipment mismatch");
            check(tag.getList("Scripts", 10).equals(full.get(j).getList("Scripts", 10)) && tag.getList("NpcLines", 10).equals(full.get(j).getList("NpcLines", 10)), "scripts/lines lost");
            check(tag.getCompound("NpcModelData").equals(full.get(j).getCompound("NpcModelData")) && tag.getCompound("TransformJob").getCompound("PuppetBody").getFloat("ScaleX") == 1.25f, "model/puppet transform lost");
            check(tag.getCompound("TransformRole").getInt("TransporterId") == -1 && tag.getCompound("TransformJob").getInt("ItemGiverId") == 0
                    && !tag.getCompound("TransformAI").contains("StartPos") && tag.getInt("ItemGiverId") == 0 && tag.getInt("TransporterId") == -1, "native transform cleanup lost");
            CompoundTag forge = tag.getCompound("ForgeData");
            check(!forge.contains("NpcSpawnerId") && !forge.contains("NpcSpawnerNr") && !forge.contains("CNPCPlusSpawnGroup") && forge.getInt("Unrelated") == 42, "group cleanup/foreign ForgeData damaged");
        }
        check(members.get(1).getString("id").equals("customnpcs:customnpc"), "native legacy NPC id conversion lost");
        CompoundTag copy = portable.copy(); copy.getList("Passengers", 10).getCompound(0).getList("Weapons", 10).getCompound(0).putString("Name", "edited");
        check(!copy.equals(portable), "portable clone aliases source");
        for (int i = 0; i < files.size(); i++) check(Arrays.equals(bytes.get(i), Files.readAllBytes(files.get(i))), "clean wrote source external data");
        for (CompoundTag tag : members) {
            UUID owner = UUID.randomUUID(); CompoundTag newSave = tag.copy();
            for (int i = 0; i < stores.size(); i++) {
                Object identity = new Object(); save.invoke(stores.get(i), identity, owner, newSave, directories.get(i));
                check(newSave.getString(IDS[i]).equals(owner.toString()), "fresh member shares old external id");
                CompoundTag again = newSave.copy(); load.invoke(stores.get(i), identity, owner, again, directories.get(i));
                check(again.getString("Name").equals(tag.getString("Name")), "fresh reload changes member");
            }
        }
        CompoundTag missing = external.copy(); CompoundTag failedMember = missing.getList("Passengers", 10).getCompound(0);
        String absent = UUID.randomUUID().toString(); failedMember.putString("CNPCPlusInvId", absent); failedMember.putString("InlineKeep", "keep");
        clean.invoke(controller, missing);
        check(failedMember.getString("CNPCPlusInvId").equals(absent) && failedMember.getString("InlineKeep").equals("keep"), "failed materialize discarded member NBT/reference");
        for (int i = 0; i < files.size(); i++) check(Arrays.equals(bytes.get(i), Files.readAllBytes(files.get(i))), "clone fork overwrote original store");
        System.out.println("CLONE_CLEAN_REGRESSION=PASS (actual mapped cleanTags + test-assembled handlers; five stores/member, native transforms, UUID removal/forks/failure retention; not runtime Mixin)");
    }
    private static InsnList hook(String name) {
        InsnList hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD, 0)); hook.add(new VarInsnNode(Opcodes.ALOAD, 1)); hook.add(new InsnNode(Opcodes.ACONST_NULL));
        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, CONTROLLER, name, "(Lnet/minecraft/nbt/CompoundTag;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", false)); return hook;
    }
    public static File getLevelSaveDirectory() { return world; }
    private static CompoundTag member(String id, String name) {
        CompoundTag tag = new CompoundTag(); tag.putString("id", id); tag.putString("Name", name); tag.putUUID("UUID", UUID.randomUUID());
        for (String key : List.of("UUIDMost", "UUIDLeast", "StartPosNew", "StartPos", "MovingPathNew", "Pos", "Riding")) tag.putString(key, "old");
        tag.putInt("ItemGiverId", 4); tag.putInt("TransporterId", 5);
        CompoundTag item = new CompoundTag(); item.putString("Name", name); item.putInt("Ammo", 17);
        ListTag weapons = new ListTag(); weapons.add(item); tag.put("Weapons", weapons);
        CompoundTag code = new CompoundTag(); code.putString("Script", "throw new Error('MUST NOT EXECUTE');"); ListTag scripts = new ListTag(); scripts.add(code); tag.put("Scripts", scripts);
        tag.put("NpcLines", weapons.copy()); tag.put("SpawnerData", weapons.copy());
        CompoundTag model = new CompoundTag(); model.putString("Model", name); tag.put("NpcModelData", model);
        CompoundTag body = new CompoundTag(); body.putFloat("ScaleX", 1.25f); CompoundTag job = new CompoundTag(); job.put("PuppetBody", body); job.putInt("ItemGiverId", 99); tag.put("TransformJob", job);
        CompoundTag role = new CompoundTag(); role.putInt("TransporterId", 99); tag.put("TransformRole", role);
        CompoundTag ai = new CompoundTag(); ai.putString("StartPos", "old"); tag.put("TransformAI", ai);
        CompoundTag forge = new CompoundTag(); forge.putString("NpcSpawnerId", "original-spawner"); forge.putInt("NpcSpawnerNr", 8); forge.putUUID("CNPCPlusSpawnGroup", UUID.randomUUID()); forge.putInt("Unrelated", 42); tag.put("ForgeData", forge);
        return tag;
    }
    private static void attach(CompoundTag parent, CompoundTag child) { ListTag list = new ListTag(); list.add(child); parent.put("Passengers", list); }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
}
