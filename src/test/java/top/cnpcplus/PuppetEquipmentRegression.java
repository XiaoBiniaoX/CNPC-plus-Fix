package top.cnpcplus;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngineManager;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.api.entity.data.role.IJobPuppet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import top.cnpcplus.script.ScriptApiCompletion;
import top.cnpcplus.script.ScriptApiDocs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Executes compiled handlers/native part methods on a tiny NPC shell, not a running Mixin/game environment. */
public final class PuppetEquipmentRegression {
    private static final String MIXIN = "top/cnpcplus/mixin/";
    private static final String JOB = "noppes/npcs/roles/JobPuppet";
    private static final String PART = JOB + "$PartConfig";
    private static final String JOB_PROBE = "top/cnpcplus/PuppetJobProbe";
    private static final String PART_PROBE = "top/cnpcplus/PuppetPartProbe";
    private static final List<String> NAMES = List.of("MainHand", "OffHand", "Helmet", "Chestplate", "Leggings", "Boots");

    public static class NpcShell { public boolean updateClient; }
    public static class JobShell {
        public final NpcShell npc = new NpcShell();
        public int getType() { return 9; }
        public IJobPuppet.IJobPuppetPart getPart(int id) throws Exception {
            var callback = new CallbackInfoReturnable<IJobPuppet.IJobPuppetPart>("getPart", true);
            getClass().getMethod("cnpcplus$getEquipmentPart", int.class, CallbackInfoReturnable.class).invoke(this, id, callback);
            return callback.getReturnValue();
        }
    }

    public static void main(String[] args) throws Exception {
        ClassNode gui = read(MIXIN + "MixinGuiNpcPuppetEquipment");
        List<String> keys = Arrays.stream(gui.methods.stream().filter(m -> m.name.equals("<clinit>")).findFirst().orElseThrow()
                        .instructions.toArray()).filter(i -> i instanceof LdcInsnNode).map(i -> ((LdcInsnNode)i).cst)
                .filter(String.class::isInstance).map(String.class::cast).toList();
        check(keys.equals(List.of("cnpcplus.equip.mainhand", "cnpcplus.equip.offhand", "cnpcplus.equip.helmet",
                "cnpcplus.equip.chestplate", "cnpcplus.equip.leggings", "cnpcplus.equip.boots")), "GUI must retain stable translation keys");
        for (String locale : List.of("zh_cn", "en_us", "ru_ru")) {
            var lang = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/cnpcplus/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : keys) check(lang.has(key) && !lang.get(key).getAsString().isBlank(), "missing " + locale + ": " + key);
        }

        ClassNode nativePart = read(PART), additions = read(MIXIN + "MixinPuppetPartConfig");
        check(nativePart.fields.stream().anyMatch(f -> f.name.equals("this$0") && f.desc.equals("L" + JOB + ";")
                && (f.access & Opcodes.ACC_FINAL) != 0), "native outer-job shadow changed");
        // Merge just this part's compiled members. Injection hooks are invoked explicitly below.
        ClassNode merged = new ClassNode();
        additions.accept(new ClassRemapper(merged, new SimpleRemapper(additions.name, PART)));
        for (var field : merged.fields) if (nativePart.fields.stream().noneMatch(f -> f.name.equals(field.name))) nativePart.fields.add(field);
        for (var method : merged.methods) if (!method.name.equals("<init>")) nativePart.methods.add(method);

        Map<String, String> remap = new HashMap<>();
        remap.put(JOB, Type.getInternalName(JobShell.class));
        remap.put("noppes/npcs/entity/EntityNPCInterface", Type.getInternalName(NpcShell.class));
        remap.put(PART, PART_PROBE);
        // CNPC resource bytecode still uses SRG names for Minecraft methods.
        remap.put("net/minecraft/nbt/CompoundTag.m_128350_(Ljava/lang/String;F)V", "putFloat");
        remap.put("net/minecraft/nbt/CompoundTag.m_128379_(Ljava/lang/String;Z)V", "putBoolean");
        remap.put("net/minecraft/nbt/CompoundTag.m_128457_(Ljava/lang/String;)F", "getFloat");
        remap.put("net/minecraft/nbt/CompoundTag.m_128471_(Ljava/lang/String;)Z", "getBoolean");
        ClassNode partProbe = new ClassNode(); nativePart.accept(new ClassRemapper(partProbe, new SimpleRemapper(remap)));
        partProbe.innerClasses.clear(); partProbe.nestHostClass = null;

        ClassNode jobMixin = read(MIXIN + "MixinJobPuppetEquipment");
        var handler = jobMixin.methods.stream().filter(m -> m.name.equals("cnpcplus$getEquipmentPart")).findFirst().orElseThrow();
        check(handler.signature.contains("IJobPuppet$IJobPuppetPart"), "getPart callback must match native interface return type");
        check(read(JOB).methods.stream().anyMatch(m -> m.name.equals("getPart")
                && m.desc.equals("(I)Lnoppes/npcs/api/entity/data/role/IJobPuppet$IJobPuppetPart;")), "native getPart descriptor changed");
        jobMixin.methods.removeIf(m -> !List.of("<init>", "<clinit>", "cnpcplus$initEquipmentParts", "cnpcplus$saveEquipmentParts",
                "cnpcplus$loadEquipmentParts", "cnpcplus$getEquipmentPart").contains(m.name));
        remap.put(jobMixin.name, JOB_PROBE);
        ClassNode jobProbe = new ClassNode(); jobMixin.accept(new ClassRemapper(jobProbe, new SimpleRemapper(remap)));
        jobProbe.access &= ~Opcodes.ACC_ABSTRACT;
        jobProbe.superName = Type.getInternalName(JobShell.class); jobProbe.interfaces.clear();
        for (var method : jobProbe.methods) {
            method.access = (method.access & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            if (method.name.equals("<init>")) for (var instruction : method.instructions)
                if (instruction instanceof MethodInsnNode call && call.name.equals("<init>")) call.owner = jobProbe.superName;
        }
        Map<String, byte[]> probes = Map.of(JOB_PROBE.replace('/', '.'), bytes(jobProbe), PART_PROBE.replace('/', '.'), bytes(partProbe));
        ClassLoader loader = new ClassLoader(PuppetEquipmentRegression.class.getClassLoader()) {
            @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] code = probes.get(name);
                if (code == null) throw new ClassNotFoundException(name);
                return defineClass(name, code, 0, code.length);
            }
        };
        Class<?> jobType = loader.loadClass(JOB_PROBE.replace('/', '.')), partType = loader.loadClass(PART_PROBE.replace('/', '.'));
        JobShell job = (JobShell)jobType.getConstructor().newInstance();
        jobType.getMethod("cnpcplus$initEquipmentParts", NpcShell.class, CallbackInfo.class).invoke(job, job.npc, new CallbackInfo("<init>", false));
        Method write = partType.getDeclaredMethod("cnpcplus$writeOffset", CallbackInfoReturnable.class); write.setAccessible(true);
        Method read = partType.getDeclaredMethod("cnpcplus$readOffset", CompoundTag.class, CallbackInfo.class); read.setAccessible(true);
        Method getPart = jobType.getMethod("cnpcplus$getEquipmentPart", int.class, CallbackInfoReturnable.class);
        for (int id = -1; id <= 24; id++) {
            if (id >= 12 && id <= 23) continue;
            var callback = new CallbackInfoReturnable<IJobPuppet.IJobPuppetPart>("getPart", true);
            getPart.invoke(job, id, callback);
            check(!callback.isCancelled(), "equipment handler intercepted native/invalid id " + id);
        }
        for (int id = 12; id <= 23; id++) {
            Object part = job.getPart(id);
            String field = "cnpcplus$" + NAMES.get((id - 12) % 6).toLowerCase(java.util.Locale.ROOT) + (id >= 18 ? "2" : "");
            check(part == jobType.getField(field).get(job), "wrong equipment ID " + id);
            check(part != job.getPart(id < 18 ? id + 6 : id - 6), "start/end configurations share state");
            job.npc.updateClient = false;
            partType.getMethod("setOffset", float.class, float.class, float.class).invoke(part, (float)id, 40f, -40f);
            check(job.npc.updateClient && (float)partType.getMethod("getOffsetX").invoke(part) == Math.min(id, 20)
                    && (float)partType.getMethod("getOffsetY").invoke(part) == 20f
                    && (float)partType.getMethod("getOffsetZ").invoke(part) == -20f, "offset clamp/sync failed " + id);
            job.npc.updateClient = false;
            partType.getMethod("setEnabled", boolean.class).invoke(part, false);
            check(job.npc.updateClient && !(boolean)partType.getMethod("isEnabled").invoke(part), "disable/sync failed");
            // Native setRotation also enables the part; keep that existing behavior.
            ((IJobPuppet.IJobPuppetPart)part).setRotation(210, 180, 150);
            check((boolean)partType.getMethod("isEnabled").invoke(part)
                    && ((IJobPuppet.IJobPuppetPart)part).getRotationX() == 210, "native rotation behavior changed");
            partType.getMethod("setEnabled", boolean.class).invoke(part, false);
            CompoundTag tag = (CompoundTag)partType.getMethod("writeNBT").invoke(part);
            write.invoke(part, new CallbackInfoReturnable<CompoundTag>("writeNBT", false, tag));
            check(tag.getFloat("PuppetOffsetY") == 20 && tag.getBoolean("Disabled"), "native/addon NBT write failed");
            Object restored = partType.getConstructor(JobShell.class).newInstance(job);
            partType.getMethod("readNBT", CompoundTag.class).invoke(restored, tag);
            read.invoke(restored, tag, new CallbackInfo("readNBT", false));
            check((float)partType.getMethod("getOffsetX").invoke(restored) == Math.min(id, 20)
                    && !(boolean)partType.getMethod("isEnabled").invoke(restored)
                    && ((IJobPuppet.IJobPuppetPart)restored).getRotationX() == 210, "part NBT roundtrip failed " + id);
            read.invoke(restored, new CompoundTag(), new CallbackInfo("readNBT", false));
            check((float)partType.getMethod("getOffsetX").invoke(restored) == 0, "old NBT must default offsets to zero");
        }
        CompoundTag all = new CompoundTag();
        jobType.getMethod("cnpcplus$saveEquipmentParts", CompoundTag.class, CallbackInfoReturnable.class)
                .invoke(job, all, new CallbackInfoReturnable<CompoundTag>("save", false, all));
        for (int id = 12; id <= 23; id++) {
            String key = "Puppet" + NAMES.get((id - 12) % 6) + (id >= 18 ? "2" : "");
            check(all.getCompound(key).getBoolean("Disabled"), "equipment save missing " + key);
            ((IJobPuppet.IJobPuppetPart)job.getPart(id)).setRotation(180, 180, 180);
        }
        jobType.getMethod("cnpcplus$loadEquipmentParts", CompoundTag.class, CallbackInfo.class).invoke(job, all, new CallbackInfo("load", false));
        for (int id = 12; id <= 23; id++) check(((IJobPuppet.IJobPuppetPart)job.getPart(id)).getRotationX() == 210, "equipment load failed " + id);

        var engine = new ScriptEngineManager().getEngineByName("nashorn");
        check(engine != null, "Nashorn required");
        engine.put("puppet", job);
        check(Boolean.TRUE.equals(engine.eval("var p = puppet.getPart(12); p.setOffset(0.25,-0.5,1); p.setEnabled(true);"
                + "p.getOffsetX() == 0.25 && p.getOffsetY() == -0.5 && p.getOffsetZ() == 1 && p.isEnabled()")), "Nashorn public method binding failed");
        job.npc.updateClient = false;
        check(Boolean.TRUE.equals(engine.eval("var rejected = false; try { p.setOffset(2,NaN,3) } catch (error) { rejected = true }"
                + "rejected && p.getOffsetX() == 0.25 && p.getOffsetY() == -0.5 && p.getOffsetZ() == 1"))
                && !job.npc.updateClient, "non-finite input partially changed/synced offsets");
        check(Boolean.TRUE.equals(engine.eval("p.setEnabled(false); p.setOffset(1,2,3); !p.isEnabled()")), "setOffset must not enable a disabled part");
        engine.eval(Files.readString(Path.of("游戏验收脚本/15-木偶装备/01-六部位设置与重置.js")));
        engine.eval("interact({npc:{getJob:function(){return puppet},say:function(text){}}})");
        check((float)partType.getMethod("getOffsetX").invoke(job.getPart(12)) == .1f, "acceptance sample did not set mainhand offset");
        engine.eval("interact({npc:{getJob:function(){return puppet},say:function(text){}}})");
        check((float)partType.getMethod("getOffsetX").invoke(job.getPart(12)) == 0f, "acceptance sample reset failed");
        check(ScriptApiCompletion.runtimeWrapper(IJobPuppet.class).getName().equals(JOB.replace('/', '.'))
                && ScriptApiCompletion.runtimeWrapper(IJobPuppet.IJobPuppetPart.class).getName().equals(PART.replace('/', '.')), "puppet metadata mapping missing");
        for (String method : List.of("getOffsetX", "getOffsetY", "getOffsetZ", "setOffset", "isEnabled", "setEnabled")) {
            check(ScriptApiCompletion.members(partType).stream().anyMatch(e -> e.name.equals(method)), "completion missing " + method);
            check(ScriptApiDocs.index(List.of(partType), name -> "CNPCplus").stream().anyMatch(line -> line.signature().startsWith(method + "(")), "docs missing " + method);
        }
        System.out.println("PUPPET_EQUIPMENT_REGRESSION=PASS (3 languages, 12 IDs, offsets/enabled/sync, native rotation/NBT, Nashorn and metadata; no game launch)");
    }

    private static ClassNode read(String name) throws Exception {
        try (InputStream stream = PuppetEquipmentRegression.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "missing " + name);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static byte[] bytes(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
}
