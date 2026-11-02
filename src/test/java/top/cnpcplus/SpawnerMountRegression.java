package top.cnpcplus;

import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.entity.data.DataStats;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Actual mapped native caller/lifecycle + production handlers, test-assembled without a Mixin launcher. */
public final class SpawnerMountRegression {
    private static sun.misc.Unsafe unsafe;
    private static Entity nextRoot;
    private static final String JOB = "noppes/npcs/roles/JobSpawner";
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException) || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
            System.out.println("HEADLESS_ONLY: test assembly, no actual Mixin/Forge transforms");
        }
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); unsafe = (sun.misc.Unsafe)field.get(null);
        ClassLoader loader = new ClassLoader(SpawnerMountRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(JOB.replace('/', '.'))) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(JOB + ".class")) {
                        ClassNode target = new ClassNode(); new ClassReader(input).accept(target, 0);
                        String mixinName = "top/cnpcplus/mixin/MixinJobSpawnerMounts";
                        ClassNode mixin = new ClassNode();
                        try (var source = getParent().getResourceAsStream(mixinName + ".class")) { new ClassReader(source).accept(mixin, 0); }
                        target.fields.addAll(mixin.fields.stream().filter(f -> f.name.startsWith("cnpcplus$")).toList());
                        for (MethodNode method : mixin.methods) if (method.name.startsWith("cnpcplus$") || method.name.startsWith("lambda$cnpcplus$")) {
                            for (var instruction : method.instructions.toArray()) {
                                if (instruction instanceof FieldInsnNode f && f.owner.equals(mixinName)) f.owner = JOB;
                                if (instruction instanceof MethodInsnNode c) {
                                    if (c.owner.equals(mixinName)) c.owner = JOB;
                                    if (c.owner.equals("noppes/npcs/packets/server/SPacketToolMobSpawner")) c.owner = "top/cnpcplus/SpawnerMountRegression";
                                }
                                if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                                    for (int i = 0; i < dynamic.bsmArgs.length; i++) if (dynamic.bsmArgs[i] instanceof Handle h && h.getOwner().equals(mixinName))
                                        dynamic.bsmArgs[i] = new Handle(h.getTag(), JOB, h.getName(), h.getDesc(), h.isInterface());
                                    dynamic.desc = dynamic.desc.replace(mixinName, JOB);
                                }
                            }
                            target.methods.add(method);
                        }
                        for (MethodNode method : target.methods) {
                            if (method.name.equals("spawnEntity") && method.desc.startsWith("(Lnet/minecraft/nbt/CompoundTag;")) {
                                for (var instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode c && c.owner.equals("noppes/npcs/packets/server/SPacketToolMobSpawner")) {
                                    int local = method.maxLocals; method.maxLocals += 8;
                                    InsnList invoke = new InsnList();
                                    invoke.add(new VarInsnNode(Opcodes.ASTORE, local)); invoke.add(new VarInsnNode(Opcodes.DSTORE, local + 1));
                                    invoke.add(new VarInsnNode(Opcodes.DSTORE, local + 3)); invoke.add(new VarInsnNode(Opcodes.DSTORE, local + 5)); invoke.add(new VarInsnNode(Opcodes.ASTORE, local + 7));
                                    invoke.add(new VarInsnNode(Opcodes.ALOAD, 0)); invoke.add(new VarInsnNode(Opcodes.ALOAD, local + 7));
                                    invoke.add(new VarInsnNode(Opcodes.DLOAD, local + 5)); invoke.add(new VarInsnNode(Opcodes.DLOAD, local + 3));
                                    invoke.add(new VarInsnNode(Opcodes.DLOAD, local + 1)); invoke.add(new VarInsnNode(Opcodes.ALOAD, local));
                                    method.instructions.insertBefore(c, invoke); c.setOpcode(Opcodes.INVOKEVIRTUAL); c.owner = JOB; c.name = "cnpcplus$trackWholeSummon";
                                }
                            }
                            for (var instruction : method.instructions.toArray()) {
                                if (method.name.equals("<init>") && instruction.getOpcode() == Opcodes.RETURN) {
                                    InsnList init = new InsnList(); init.add(new VarInsnNode(Opcodes.ALOAD, 0)); init.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
                                    init.add(new InsnNode(Opcodes.DUP)); init.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false));
                                    init.add(new FieldInsnNode(Opcodes.PUTFIELD, JOB, "cnpcplus$mounts", "Ljava/util/List;")); method.instructions.insertBefore(instruction, init);
                                }
                                if ((method.name.equals("checkSpawns") || method.name.equals("removeAllSpawned")) && instruction.getOpcode() == Opcodes.RETURN) {
                                    InsnList hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD, 0)); hook.add(new InsnNode(Opcodes.ACONST_NULL));
                                    hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, JOB, method.name.equals("checkSpawns") ? "cnpcplus$removeOrphanMounts" : "cnpcplus$removeMounts", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", false));
                                    method.instructions.insertBefore(instruction, hook);
                                }
                                if (method.name.equals("getNearbySpawned") && instruction.getOpcode() == Opcodes.ARETURN) {
                                    int local = method.maxLocals++; InsnList hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ASTORE, local));
                                    hook.add(new VarInsnNode(Opcodes.ALOAD, 0)); hook.add(new TypeInsnNode(Opcodes.NEW, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable"));
                                    hook.add(new InsnNode(Opcodes.DUP)); hook.add(new LdcInsnNode("getNearbySpawned")); hook.add(new InsnNode(Opcodes.ICONST_0)); hook.add(new VarInsnNode(Opcodes.ALOAD, local));
                                    hook.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable", "<init>", "(Ljava/lang/String;ZLjava/lang/Object;)V", false));
                                    hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, JOB, "cnpcplus$recoverMounts", "(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V", false));
                                    hook.add(new VarInsnNode(Opcodes.ALOAD, local)); method.instructions.insertBefore(instruction, hook);
                                }
                            }
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); target.accept(writer); byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        Class<?> jobType = loader.loadClass(JOB.replace('/', '.'));
        World world = (World)unsafe.allocateInstance(World.class); world.entities = new ArrayList<>();
        Npc owner = npc(world), target = npc(world); owner.attackTarget = target;
        Object job = jobType.getConstructor(EntityNPCInterface.class).newInstance(owner);
        set(jobType, job, "target", target); // aiShouldExecute normally establishes this before native checkSpawns.
        var spawn = jobType.getDeclaredMethod("spawnEntity", CompoundTag.class); spawn.setAccessible(true);
        var nearby = jobType.getDeclaredMethod("getNearbySpawned"); nearby.setAccessible(true);
        Field spawnedField = jobType.getField("spawned"), mountsField = jobType.getDeclaredField("cnpcplus$mounts"); mountsField.setAccessible(true);
        CompoundTag tag = new CompoundTag(); tag.putString("id", "minecraft:boat");
        Mount boat = mount(world); Npc rider = npc(world), passenger = npc(world); ride(rider, boat); ride(passenger, rider); nextRoot = boat;
        check(spawn.invoke(job, tag) == rider, "nonLiving root did not return first living");
        List<?> spawned = (List<?>)spawnedField.get(job);
        check(spawned.size() == 2 && spawned.contains(rider) && spawned.contains(passenger), "missing or duplicate living member");
        UUID group = boat.getPersistentData().getUUID("CNPCPlusSpawnGroup");
        for (Entity e : List.of(boat, rider, passenger)) check(e.getPersistentData().getUUID("CNPCPlusSpawnGroup").equals(group)
                && !e.getPersistentData().getString("NpcSpawnerId").isEmpty(), "summon identity not shared");
        check(rider.attackTarget == target && passenger.attackTarget == target && rider.stats.spawnCycle == 4 && passenger.stats.spawnCycle == 4
                && !rider.ais.returnToStart && passenger.stats.respawnTime == 0, "native target/NPC lifecycle lost");
        check(((List<?>)mountsField.get(job)).equals(List.of(boat)), "nonLiving mount not tracked");
        jobType.getMethod("checkSpawns").invoke(job); check(!boat.isRemoved(), "active mount removed");
        rider.dead = true; jobType.getMethod("checkSpawns").invoke(job);
        check(((List<?>)spawnedField.get(job)).equals(List.of(passenger)) && !boat.isRemoved(), "rider death lost remaining passenger");
        detach(passenger); passenger.dead = true; jobType.getMethod("checkSpawns").invoke(job);
        check(boat.isRemoved() && ((List<?>)mountsField.get(job)).isEmpty(), "dismounted orphan survived");
        Mount empty = mount(world); nextRoot = empty; check(spawn.invoke(job, tag) == null && empty.isRemoved(), "noLiving summon leaked");
        nextRoot = null; check(spawn.invoke(job, tag) == null, "failed factory accepted");
        Mount restored = mount(world), unrelated = mount(world), orphan = mount(world); Npc living = npc(world);
        String id = boat.getPersistentData().getString("NpcSpawnerId"); UUID restoredGroup = UUID.randomUUID();
        for (Entity e : List.of(restored, living)) { e.getPersistentData().putString("NpcSpawnerId", id); e.getPersistentData().putUUID("CNPCPlusSpawnGroup", restoredGroup); }
        orphan.getPersistentData().putString("NpcSpawnerId", id); orphan.getPersistentData().putUUID("CNPCPlusSpawnGroup", UUID.randomUUID());
        world.entities = new ArrayList<>(List.of(restored, living, unrelated, orphan));
        spawnedField.set(job, nearby.invoke(job));
        check(((List<?>)mountsField.get(job)).equals(List.of(restored)) && orphan.isRemoved() && !unrelated.isRemoved(), "restart recovery/orphan/ownership failed");
        jobType.getMethod("removeAllSpawned").invoke(job);
        check(restored.isRemoved() && living.isRemoved() && ((List<?>)mountsField.get(job)).isEmpty() && !unrelated.isRemoved(), "native removeAll left owned members or touched unrelated");
        Mount lone = mount(world); lone.getPersistentData().putString("NpcSpawnerId", id); lone.getPersistentData().putUUID("CNPCPlusSpawnGroup", UUID.randomUUID());
        world.entities = new ArrayList<>(List.of(lone)); nearby.invoke(job); check(lone.isRemoved(), "emptyLiving recovery leaked orphan");
        System.out.println("SPAWNER_MOUNT_REGRESSION=PASS (actual mapped native caller/lifecycle; test-assembled handlers/world/factory, not runtime Mixin)");
    }
    public static Entity spawnClone(CompoundTag ignored, double x, double y, double z, Level world) { return nextRoot; }
    private static Npc npc(World world) throws Exception {
        Npc npc = (Npc)unsafe.allocateInstance(Npc.class); init(npc, world);
        npc.stats = (DataStats)unsafe.allocateInstance(DataStats.class); npc.stats.spawnCycle = 1; npc.stats.respawnTime = 20;
        set(EntityNPCInterface.class, npc, "ais", unsafe.allocateInstance(DataAI.class)); npc.ais.returnToStart = true;
        set(Entity.class, npc, "random", RandomSource.create(1)); return npc;
    }
    private static Mount mount(World world) throws Exception { Mount mount = (Mount)unsafe.allocateInstance(Mount.class); init(mount, world); return mount; }
    private static void init(Entity entity, World world) throws Exception {
        set(Entity.class, entity, "level", world); set(Entity.class, entity, "passengers", ImmutableList.of()); set(Entity.class, entity, "uuid", UUID.randomUUID());
        set(Entity.class, entity, "position", net.minecraft.world.phys.Vec3.ZERO);
        entity.setBoundingBox(new AABB(0, 0, 0, 1, 2, 1));
    }
    private static void ride(Entity rider, Entity mount) throws Exception {
        var passengers = new ArrayList<>(mount.getPassengers()); passengers.add(rider); set(Entity.class, mount, "passengers", ImmutableList.copyOf(passengers)); set(Entity.class, rider, "vehicle", mount);
    }
    private static void detach(Entity rider) throws Exception { set(Entity.class, rider, "vehicle", null); }
    private static void set(Class<?> type, Object object, String name, Object value) throws Exception { Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
    public static final class Npc extends EntityCustomNpc {
        boolean dead; LivingEntity attackTarget;
        private Npc() { super(null, null); }
        @Override public boolean isDeadOrDying() { return dead || isRemoved(); }
        @Override public float getHealth() { return dead ? 0 : 20; }
        @Override public LivingEntity getTarget() { return attackTarget; }
        @Override public void setTarget(LivingEntity target) { attackTarget = target; }
        @Override public boolean isInRange(Entity entity, double range) { return true; }
        @Override public void setPos(double x, double y, double z) {}
        @Override public void remove(RemovalReason reason) { try { set(Entity.class, this, "removalReason", reason); } catch (Exception e) { throw new AssertionError(e); } }
    }
    public static final class Mount extends Entity {
        private Mount() { super(null, null); }
        @Override protected void defineSynchedData() {}
        @Override protected void readAdditionalSaveData(CompoundTag tag) {}
        @Override protected void addAdditionalSaveData(CompoundTag tag) {}
        @Override public void remove(RemovalReason reason) { try { set(Entity.class, this, "removalReason", reason); } catch (Exception e) { throw new AssertionError(e); } }
    }
    public static final class World extends ServerLevel {
        List<Entity> entities;
        private World() { super(null, null, null, null, null, null, null, false, 0L, List.of(), false, null); }
        @Override public long getGameTime() { return 100; }
        @Override public <T extends Entity> List<T> getEntitiesOfClass(Class<T> type, AABB box) { return entities.stream().filter(type::isInstance).map(type::cast).toList(); }
    }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
