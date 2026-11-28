package top.cnpcplus;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeConfigSpec;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Port of the 1.21.1 replay: native 1.20.1 MoveControl and actual production callbacks. */
public final class ReturnHomeRegression {
    public enum Operation { WAIT, MOVE_TO, STRAFE, JUMPING }
    public static class Jump { int jumps; public void jump() { jumps++; } }
    public static class Navigation {
        boolean done = true;
        public boolean isDone() { return done; }
        public NodeEvaluator getNodeEvaluator() { return null; }
    }
    public static class GroundNavigation extends Navigation {}
    public static class Ai {
        public int orientation = 30;
        public boolean mountControl;
        int animation = 2;
        public int getStandingType() { return 1; }
        public int getAnimation() { return animation; }
    }
    public static class World implements BlockGetter {
        final BlockState support;
        World(boolean slab) { support = slab ? Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM) : Blocks.STONE.defaultBlockState(); }
        public BlockState getBlockState(BlockPos pos) { return pos.getY() < 64 ? support : Blocks.AIR.defaultBlockState(); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getHeight() { return 384; }
        public int getMinBuildHeight() { return -64; }
    }
    public static class Npc implements top.cnpcplus.ai.NpcControlAccess {
        public boolean cnpcplus$isFrozen() { return false; }
        public void cnpcplus$setFrozen(boolean frozen) {}
        public boolean cnpcplus$isDisengaging() { return false; }
        public boolean cnpcplus$isForcedReturning() { return forced; }
        double x, y = 64, z, vx, vy = -.08, vz, speed = .25, startY = 65;
        float yaw, bodyYaw, headYaw, zza, xxa;
        boolean ground = true, water, ladder, forced, attacking, interacting, killed, vehicle;
        int animation = -1, dimensionRefreshes;
        public boolean updateAI;
        public Ai ais = new Ai();
        Object driver;
        World world = new World(false);
        Navigation navigation = new GroundNavigation();
        Jump jump = new Jump();
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public float getStartXPos() { return .5f; }
        public float getStartZPos() { return .5f; }
        public double getStartYPos() { return startY; }
        public float getStepHeight() { return .6f; }
        public float getBbWidth() { return .6f; }
        public boolean onGround() { return ground; }
        public boolean isInWaterOrBubble() { return water; }
        public boolean onClimbable() { return ladder; }
        public Navigation getNavigation() { return navigation; }
        public World level() { return world; }
        public double getAttributeValue(Attribute attribute) { return speed; }
        // Match CNPC.getSpeed and Mob.setSpeed: these are intentionally asymmetric.
        public float getSpeed() { return (float)speed; }
        public void setSpeed(float value) { zza = value; }
        public void setZza(float value) { zza = value; }
        public void setXxa(float value) { xxa = value; }
        public float getYRot() { return yaw; }
        public void setYRot(float value) { yaw = value; }
        public int getBlockY() { return (int)Math.floor(y); }
        public BlockPos blockPosition() { return BlockPos.containing(x, y, z); }
        public Jump getJumpControl() { return jump; }
        public Object getMoveControl() { return driver; }
        public Vec3 getDeltaMovement() { return new Vec3(vx, vy, vz); }
        public void setDeltaMovement(double x, double y, double z) { vx = x; vy = y; vz = z; }
        public boolean isAttacking() { return attacking; }
        public boolean isInteracting() { return interacting; }
        public boolean isKilled() { return killed; }
        public boolean isVehicle() { return vehicle; }
        public boolean isAlive() { return !killed; }
        // Standing NPC's synced Walking follows navigation.isDone in native aiStep.
        public boolean isWalking() { return attacking || !navigation.isDone(); }
        public void setYBodyRot(float value) { bodyYaw = value; }
        public void setYHeadRot(float value) { headYaw = value; }
        public void setCurrentAnimation(int value) { animation = value; }
        public void refreshDimensions() { dimensionRefreshes++; }
    }
    public static class Puppet {
        public Npc npc;
        public boolean whileAttacking, whileMoving, whileStanding = true;
    }
    public static class Config {
        static boolean enabled = true;
        public static boolean bool(ForgeConfigSpec.BooleanValue value, boolean fallback) { return enabled; }
    }
    private static final String BASE = "top/cnpcplus/ReturnHomeRegression";
    private static final String NPC = "noppes/npcs/entity/EntityNPCInterface";
    private static final String STATE = "top/cnpcplus/ai/ReturnHomeState";
    private static final String MOVE = "net/minecraft/world/entity/ai/control/MoveControl";
    private static final String MIXIN = "top/cnpcplus/mixin/MixinReturnHomeMoveControl";
    private static final String GOAL = "top/cnpcplus/mixin/MixinEntityAIReturnSmoothArrival";
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry(NPC, BASE + "$Npc"), Map.entry("net/minecraft/world/entity/Mob", BASE + "$Npc"),
            Map.entry("net/minecraft/world/level/Level", BASE + "$World"),
            Map.entry("net/minecraft/world/entity/ai/navigation/PathNavigation", BASE + "$Navigation"),
            Map.entry("net/minecraft/world/entity/ai/navigation/GroundPathNavigation", BASE + "$GroundNavigation"),
            Map.entry("net/minecraft/world/entity/ai/control/JumpControl", BASE + "$Jump"),
            Map.entry("noppes/npcs/entity/data/DataAI", BASE + "$Ai"),
            Map.entry(MOVE + "$Operation", BASE + "$Operation"),
            Map.entry(MOVE, "ReplayReturnMove"), Map.entry(MIXIN, "ReplayReturnMove"),
            Map.entry(STATE, "ReplayReturnState"), Map.entry(GOAL, "ReplayReturnGoal"),
            Map.entry("noppes/npcs/roles/JobPuppet", "ReplayStandingPuppet"),
            Map.entry("top/cnpcplus/config/ServerConfigAccess", BASE + "$Config"));
    private static class Loader extends ClassLoader {
        Class<?> load(ClassNode node) {
            var output = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(new ClassRemapper(output, new SimpleRemapper(TYPES)));
            byte[] bytes = output.toByteArray();
            return defineClass(TYPES.getOrDefault(node.name, node.name).replace('/', '.'), bytes, 0, bytes.length);
        }
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage())) throw unavailable;
            System.out.println("HEADLESS_ONLY: untransformed Forge network hook; not a loader smoke test");
        }
        try (var jar = new ZipFile("libs/CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260711.jar")) {
            ClassNode nativeGoal = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getEntry("noppes/npcs/ai/EntityAIReturn.class"))).accept(nativeGoal, 0);
            MethodNode navigate = nativeGoal.methods.stream().filter(m -> m.name.equals("navigate")).findFirst().orElseThrow();
            check(Arrays.stream(navigate.instructions.toArray()).filter(i -> i instanceof MethodInsnNode c
                    && c.owner.equals(NPC) && c.name.equals("getStartYPos") && c.desc.equals("()D")).count() == 1, "ground Y redirect anchor");
            check(nativeGoal.methods.stream().anyMatch(m -> m.name.equals("m_8041_") && m.desc.equals("()V")), "native stop target");
        }
        var loader = new Loader();
        Class<?> state = loader.load(read(STATE));
        ClassNode goal = read(GOAL);
        goal.access &= ~Opcodes.ACC_ABSTRACT;
        goal.fields.removeIf(f -> !f.name.equals("npc"));
        goal.fields.get(0).access = Opcodes.ACC_PUBLIC;
        goal.methods.removeIf(m -> !List.of("<init>", "cnpcplus$returnToGroundSurface", "cnpcplus$releaseReturnControl", "cnpcplus$skipControlledMount").contains(m.name));
        for (var method : goal.methods) for (var instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode field && field.owner.equals("top/cnpcplus/config/CnpcPlusServerConfig"))
                method.instructions.set(field, new InsnNode(Opcodes.ACONST_NULL));
            if (instruction instanceof MethodInsnNode call && call.name.equals("getMoveControl")) call.desc = "()Ljava/lang/Object;";
            if (instruction instanceof MethodInsnNode call && call.owner.equals(MOVE) && call.name.equals("setWantedPosition"))
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, BASE, "setWanted", "(Ljava/lang/Object;DDDD)V", false));
        }
        Class<?> goalType = loader.load(goal);
        Object goalInstance = goalType.getConstructor().newInstance();
        Method normalize = goalType.getDeclaredMethod("cnpcplus$returnToGroundSurface", Npc.class);
        normalize.setAccessible(true);
        Method start = state.getMethod("start", Npc.class, Vec3.class);
        Method approach = state.getMethod("finalApproach", Npc.class);
        Method arrived = state.getMethod("arrived", Npc.class, Vec3.class);
        Method release = goalType.getDeclaredMethod("cnpcplus$releaseReturnControl", org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        release.setAccessible(true);
        Npc npc = new Npc(); npc.x = .85; npc.z = .5;
        start.invoke(null, npc, new Vec3(.5, npc.startY, .5));
        check(approach.invoke(null, npc) == null, "old cached Y must reproduce rejected handoff");
        check((double)normalize.invoke(goalInstance, npc) == 64, "cached +1 not normalized");
        npc.startY = 64;
        check((double)normalize.invoke(goalInstance, npc) == 64, "cold cache changed physical floor");
        npc.world = new World(true); npc.startY = 64.5; npc.y = 63.5;
        check((double)normalize.invoke(goalInstance, npc) == 63.5, "slab floor lost fractional height");
        Config.enabled = false;
        check((double)normalize.invoke(goalInstance, npc) == 64.5, "disabled option changed native Y");
        npc.forced = true;
        check((double)normalize.invoke(goalInstance, npc) == 63.5, "forced return did not correct floor");
        Config.enabled = true; npc.navigation = new Navigation();
        check((double)normalize.invoke(goalInstance, npc) == 64.5, "non-ground navigation Y changed");

        // Execute CNPC's actual standing-puppet gate with the explicit Walking boundary above.
        ClassNode puppet = read("noppes/npcs/roles/JobPuppet");
        puppet.superName = BASE + "$Puppet"; puppet.fields.clear(); puppet.interfaces.clear();
        puppet.methods.removeIf(m -> !m.name.equals("isActive"));
        for (var insn : puppet.methods.get(0).instructions) if (insn instanceof MethodInsnNode c && c.name.equals("m_6084_")) c.name = "isAlive";
        var constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BASE + "$Puppet", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN)); puppet.methods.add(constructor);
        Class<?> puppetType = loader.load(puppet);
        Puppet standingPuppet = (Puppet)puppetType.getConstructor().newInstance();
        Method active = puppetType.getMethod("isActive");

        ClassNode control = read(MOVE);
        var handler = read(MIXIN).methods.stream().filter(m -> m.name.equals("cnpcplus$walkFinalGap")).findFirst().orElseThrow();
        control.methods.add(handler);
        var tick = control.methods.stream().filter(m -> m.name.equals("tick")).findFirst().orElseThrow();
        var head = new InsnList(); head.add(new VarInsnNode(Opcodes.ALOAD, 0)); head.add(new InsnNode(Opcodes.ACONST_NULL));
        head.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MOVE, handler.name, handler.desc, false)); tick.instructions.insert(head);
        Class<?> controller = loader.load(control);
        Method controlTick = controller.getMethod("tick");
        int cases = 0, worst = 0;
        for (double speed : new double[]{.025, .1, .25, .5, 1, 5}) for (double friction : new double[]{.6, .98})
            for (double angle : new double[]{0, .7, 2.5}) for (double radius : new double[]{.4, 1.4}) {
            npc = new Npc(); npc.speed = speed;
            npc.x = .5 + Math.cos(angle) * radius; npc.z = .5 + Math.sin(angle) * radius; npc.yaw = 125;
            Vec3 destination = new Vec3(.5, (double)normalize.invoke(goalInstance, npc), .5);
            start.invoke(null, npc, destination);
            Object driver = controller.getConstructor(Npc.class).newInstance(npc); npc.driver = driver;
            int steps = 0;
            for (; steps < 12000 && !(boolean)arrived.invoke(null, npc, destination); steps++) {
                controlTick.invoke(driver);
                // Native ground travel formula; world collision and network are outside this replay.
                double acceleration = npc.speed * (.21600002 / (friction * friction * friction));
                double yaw = Math.toRadians(npc.yaw);
                npc.vx += -Math.sin(yaw) * npc.zza * .98 * acceleration;
                npc.vz += Math.cos(yaw) * npc.zza * .98 * acceleration;
                npc.x += npc.vx; npc.z += npc.vz;
                npc.vx *= friction * .91; npc.vz *= friction * .91;
                if (Math.abs(npc.vx) < .003) npc.vx = 0;
                if (Math.abs(npc.vz) < .003) npc.vz = 0;
            }
            check(steps < 12000, "not converged: speed=" + speed + " friction=" + friction + " angle=" + angle);
            check(npc.jump.jumps == 0, "flat-ground return requested jump");
            npc.xxa = .2f; controlTick.invoke(driver);
            check(npc.vx == 0 && npc.vz == 0 && npc.zza == 0 && npc.xxa == 0, "arrived controller left drift/input");
            npc.vx = .1; npc.vz = -.1; npc.zza = .4f; npc.xxa = .2f;
            npc.ais.animation = cases % 8;
            goalType.getField("npc").set(goalInstance, npc); release.invoke(goalInstance, new Object[]{null});
            controlTick.invoke(driver);
            check(npc.vx == 0 && npc.vz == 0 && npc.zza == 0 && npc.xxa == 0 && npc.vy == -.08, "stop left drift/input or erased gravity");
            check(state.getMethod("target", Npc.class).invoke(null, npc) == null && npc.updateAI, "return ownership not released");
            check(npc.yaw == 30 && npc.bodyYaw == 30 && npc.headYaw == 30, "standing orientation not restored");
            check(npc.animation == npc.ais.animation && npc.dimensionRefreshes == 1, "configured animation/dimensions not restored");
            standingPuppet.npc = npc;
            check((boolean)active.invoke(standingPuppet), "standing puppet not eligible after stop");
            worst = Math.max(worst, steps); cases++;
        }
        Method mountGuard = goalType.getDeclaredMethod("cnpcplus$skipControlledMount", CallbackInfoReturnable.class);
        mountGuard.setAccessible(true); npc = new Npc(); npc.vehicle = true; npc.ais.mountControl = true;
        goalType.getField("npc").set(goalInstance, npc);
        var callback = new CallbackInfoReturnable<Boolean>("canUse", true); mountGuard.invoke(goalInstance, callback);
        check(callback.isCancelled() && !callback.getReturnValue(), "controlled mount allowed return goal");
        npc.ais.mountControl = false; callback = new CallbackInfoReturnable<>("canUse", true); mountGuard.invoke(goalInstance, callback);
        check(!callback.isCancelled(), "uncontrolled NPC blocked");
        for (int interruption = 0; interruption < 4; interruption++) {
            npc = new Npc(); npc.x = .5; npc.z = .5;
            npc.attacking = interruption == 0; npc.interacting = interruption == 1; npc.killed = interruption == 2;
            if (interruption == 3) npc.x = 1;
            start.invoke(null, npc, new Vec3(.5, 64, .5));
            goalType.getField("npc").set(goalInstance, npc); release.invoke(goalInstance, new Object[]{null});
            check(npc.animation == -1 && !npc.updateAI && npc.dimensionRefreshes == 0, "interrupted return forced idle animation");
        }
        System.out.println("RETURN_HOME_REGRESSION=PASS cases=" + cases + " maxTicks=" + worst
                + " floor/slab/forced/config/native-controller/stop/animation/puppet/mount/interruption");
        System.out.println("HEADLESS_ONLY: entity/world/synced Walking are boundaries; modeled ground travel; no game or Mixin engine; production timeout unchanged");
    }
    public static void setWanted(Object driver, double x, double y, double z, double speed) throws Exception {
        driver.getClass().getMethod("setWantedPosition", double.class, double.class, double.class, double.class).invoke(driver, x, y, z, speed);
    }
    private static ClassNode read(String name) throws Exception {
        var node = new ClassNode();
        Path artifact = Path.of(System.getProperty("returnHome.artifact", "build/classes/java/main"));
        if (name.startsWith("top/cnpcplus/") && java.nio.file.Files.isRegularFile(artifact)) {
            // The release jar uses SRG member names; restore Mojmap before replay.
            var names = new HashMap<String, String>();
            for (String line : java.nio.file.Files.readAllLines(Path.of("build/createMcpToSrg/output.tsrg"))) {
                if (line.startsWith("\t") && !line.startsWith("\t\t")) {
                    String[] parts = line.trim().split("\\s+");
                    names.put(parts[parts.length - 1], parts[0]);
                }
            }
            var mapping = new Remapper() {
                @Override public String mapMethodName(String owner, String member, String descriptor) {
                    return names.getOrDefault(member, member);
                }
                @Override public String mapFieldName(String owner, String member, String descriptor) {
                    return names.getOrDefault(member, member);
                }
            };
            try (var jar = new ZipFile(artifact.toFile())) {
                new ClassReader(jar.getInputStream(jar.getEntry(name + ".class")))
                        .accept(new ClassRemapper(node, mapping), 0);
            }
        } else try (var input = ReturnHomeRegression.class.getResourceAsStream("/" + name + ".class")) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
}
