package top.cnpcplus;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Real native + Mixin static initializers and native ID allocator, without starting Minecraft. */
public final class ProjectileSyncRegression {
    private static final String PROJECTILE = "noppes/npcs/entity/EntityProjectile";
    private static final String STORE = "top/cnpcplus/ranged/RangedSpinStore";
    private static final String MIXIN = "top/cnpcplus/mixin/MixinEntityProjectileSpin";
    private static final String ENTITY = "net/minecraft/world/entity/Entity";
    private static final String DATA = "net/minecraft/network/syncher/SynchedEntityData";
    private static final String SERIALIZERS = "net/minecraft/network/syncher/EntityDataSerializers";
    private static final String ACCESSOR = "Lnet/minecraft/network/syncher/EntityDataAccessor;";
    private static final List<String> NATIVE = List.of("Gravity", "Arrow", "Is3d", "Glows", "Rotating", "Sticks",
            "ItemStackThrown", "Velocity", "Size", "Particle");
    private static final List<String> SPIN = List.of("cnpcplus$spinX", "cnpcplus$spinY", "cnpcplus$spinZ");

    public static void main(String[] args) throws Exception {
        ClassNode store = read(STORE), mixin = read(MIXIN), projectile = read(PROJECTILE);
        for (MethodNode method : store.methods) for (var instruction : method.instructions)
            check(!(instruction instanceof MethodInsnNode call && call.owner.equals(DATA) && call.name.equals("defineId")),
                    "storage must not allocate projectile IDs");
        for (String name : SPIN) {
            FieldNode field = mixin.fields.stream().filter(f -> f.name.equals(name)).findFirst().orElseThrow();
            check((field.access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) ==
                    (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL), "spin ID must be private static final");
            check(field.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Unique;")), "spin ID needs @Unique");
        }
        for (MethodNode method : mixin.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(DATA) && call.name.equals("defineId"))
                check(method.name.equals("<clinit>"), "ID allocated per instance");
            if (instruction instanceof FieldInsnNode field && field.owner.equals(STORE))
                check(!field.desc.equals(ACCESSOR), "sync consumer still uses storage accessors");
        }
        check(registrations(initializer(projectile)).equals(NATIVE), "native projectile layout changed");
        check(registrations(initializer(mixin)).equals(SPIN), "three spin IDs not registered exactly once");
        for (String handler : List.of("cnpcplus$defineSpinFactors", "cnpcplus$snapshotSpinFactors", "cnpcplus$advanceContinuousSpin")) {
            MethodNode method = mixin.methods.stream().filter(m -> m.name.equals(handler)).findFirst().orElseThrow();
            List<String> reads = Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof FieldInsnNode field
                    && field.getOpcode() == Opcodes.GETSTATIC && field.owner.equals(MIXIN) && field.desc.equals(ACCESSOR))
                    .map(i -> ((FieldInsnNode)i).name).toList();
            check(reads.equals(SPIN), "define/snapshot/tick must use the same three IDs: " + handler);
        }
        int[] storageFirst = run(true), projectileFirst = run(false);
        check(Arrays.equals(storageFirst, projectileFirst), "client/server load order changes IDs");
        System.out.println("PROJECTILE_SYNC_REGRESSION=PASS " + Arrays.toString(storageFirst)
                + " (native allocator + actual Mixin appendInsns, both load orders, NBT/XYZ angles; not game reconnect)");
    }

    private static int[] run(boolean storageFirst) throws Exception {
        ClassLoader loader = new ClassLoader(ProjectileSyncRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                String path = name.replace('.', '/');
                if (!List.of(PROJECTILE, STORE, ENTITY, DATA, SERIALIZERS, "net/minecraft/world/entity/projectile/Projectile",
                        "net/minecraft/world/entity/projectile/ThrowableProjectile", "noppes/npcs/entity/data/DataRanged").contains(path))
                    return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try {
                        ClassNode node;
                        if (path.equals(ENTITY) || path.endsWith("/DataRanged") || path.startsWith("net/minecraft/world/entity/projectile/")) {
                            String parent = path.endsWith("/ThrowableProjectile") ? "net/minecraft/world/entity/projectile/Projectile"
                                    : path.endsWith("/Projectile") ? ENTITY : "java/lang/Object";
                            node = new ClassNode(); node.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, path, null, parent, null);
                        } else if (path.equals(SERIALIZERS)) {
                            // Replace only registry/bootstrap dependencies; the ID allocator remains native.
                            node = read(path); node.methods.clear(); node.fields.removeIf(f -> !List.of("BOOLEAN", "INT", "FLOAT", "ITEM_STACK").contains(f.name));
                            MethodNode init = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                            for (FieldNode field : node.fields) {
                                init.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "top/cnpcplus/ProjectileSyncRegression", "serializer",
                                        "()Lnet/minecraft/network/syncher/EntityDataSerializer;", false));
                                init.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, path, field.name, field.desc));
                            }
                            init.instructions.add(new InsnNode(Opcodes.RETURN)); node.methods.add(init);
                        } else {
                            node = read(path);
                            if (path.equals(DATA)) node.methods.removeIf(m -> !m.name.equals("<clinit>") && !m.name.equals("defineId"));
                            if (path.equals(PROJECTILE)) {
                                node.methods.removeIf(m -> !m.name.equals("<clinit>")); node.fields.removeIf(f -> !NATIVE.contains(f.name));
                                // The supplied CNPC dependency is SRG; map only its verified native registration names.
                                for (var instruction : initializer(node).instructions) {
                                    if (instruction instanceof MethodInsnNode call && call.owner.equals(DATA) && call.name.equals("m_135353_")) call.name = "defineId";
                                    if (instruction instanceof FieldInsnNode field && field.owner.equals(SERIALIZERS))
                                        field.name = switch (field.name) { case "f_135035_" -> "BOOLEAN"; case "f_135033_" -> "ITEM_STACK"; case "f_135028_" -> "INT"; default -> field.name; };
                                }
                                ClassNode source = read(MIXIN); node.fields.addAll(source.fields.stream().filter(f -> SPIN.contains(f.name)).toList());
                                MethodNode init = initializer(source);
                                for (var instruction : init.instructions) if (instruction instanceof FieldInsnNode field && field.owner.equals(MIXIN)) field.owner = PROJECTILE;
                                // Invoke the installed Mixin 0.8.5 merge implementation, not a handwritten approximation.
                                Class<?> applicatorType = Class.forName("org.spongepowered.asm.mixin.transformer.MixinApplicatorStandard");
                                Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
                                var unsafe = (sun.misc.Unsafe)unsafeField.get(null);
                                Object applicator = unsafe.allocateInstance(applicatorType);
                                Field target = applicatorType.getDeclaredField("targetClass"); target.setAccessible(true); target.set(applicator, node);
                                var append = applicatorType.getDeclaredMethod("appendInsns", Class.forName("org.spongepowered.asm.mixin.transformer.MixinTargetContext", false, applicatorType.getClassLoader()), MethodNode.class);
                                append.setAccessible(true); append.invoke(applicator, null, init);
                            }
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception failure) { throw new ClassNotFoundException(name, failure); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        String projectileName = PROJECTILE.replace('/', '.'), storeName = STORE.replace('/', '.');
        if (storageFirst) Class.forName(storeName, true, loader);
        Class<?> type = Class.forName(projectileName, !storageFirst, loader);
        Class<?> storage = Class.forName(storeName, true, loader);
        Class<?> rangedType = loader.loadClass("noppes.npcs.entity.data.DataRanged");
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe)unsafeField.get(null); Object ranged = unsafe.allocateInstance(rangedType);
        var put = storage.getMethod("put", rangedType, int.class, float.class);
        for (int axis = 0; axis < 3; axis++) put.invoke(null, ranged, axis, (axis - 1) * 2.5f);
        CompoundTag nbt = new CompoundTag(); storage.getMethod("write", rangedType, CompoundTag.class).invoke(null, ranged, nbt);
        check(nbt.getFloat("CNPCPlusSpinSpeed") == -2.5f && nbt.getFloat("CNPCPlusSpinSpeedZ") == 2.5f, "legacy/XYZ NBT lost");
        storage.getMethod("clear", rangedType).invoke(null, ranged);
        storage.getMethod("read", rangedType, CompoundTag.class).invoke(null, ranged, nbt);
        for (int axis = 0; axis < 3; axis++) check((float)storage.getMethod("get", rangedType, int.class).invoke(null, ranged, axis) == (axis - 1) * 2.5f, "NBT roundtrip changed spin");
        if (storageFirst) {
            Class<?> data = Class.forName(DATA.replace('/', '.'), true, loader);
            Field pool = data.getDeclaredField("ENTITY_ID_POOL"); pool.setAccessible(true);
            check(!((java.util.Map<?, ?>)pool.get(null)).containsKey(type), "DataRanged/NBT storage reserved IDs before projectile initialization");
        }
        Class.forName(projectileName, true, loader);
        List<String> fields = new java.util.ArrayList<>(NATIVE); fields.addAll(SPIN);
        int[] ids = new int[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            Field field = type.getDeclaredField(fields.get(i)); field.setAccessible(true);
            ids[i] = ((EntityDataAccessor<?>)field.get(null)).getId();
            check(ids[i] == i, "extension displaced native ID: " + field.getName());
        }
        Object entity = unsafe.allocateInstance(type);
        storage.getMethod("advanceAngles", type, float.class, float.class, float.class).invoke(null, entity, 10f, -20f, 30f);
        for (int axis = 0; axis < 3; axis++) check((float)storage.getMethod("angleOf", type, int.class, float.class).invoke(null, entity, axis, 0.5f) == new float[]{5f, -10f, 15f}[axis], "continuous XYZ interpolation changed");
        return ids;
    }

    public static EntityDataSerializer<Object> serializer() {
        return new EntityDataSerializer<>() {
            public void write(FriendlyByteBuf buffer, Object value) { throw new AssertionError("not a codec test"); }
            public Object read(FriendlyByteBuf buffer) { throw new AssertionError("not a codec test"); }
            public Object copy(Object value) { return value; }
        };
    }
    private static ClassNode read(String path) throws Exception {
        try (var input = ProjectileSyncRegression.class.getClassLoader().getResourceAsStream(path + ".class")) {
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
    private static MethodNode initializer(ClassNode node) { return node.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow(); }
    private static List<String> registrations(MethodNode method) {
        return Arrays.stream(method.instructions.toArray()).filter(i -> i instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC && field.desc.equals(ACCESSOR))
                .map(i -> ((FieldInsnNode)i).name).toList();
    }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
}
