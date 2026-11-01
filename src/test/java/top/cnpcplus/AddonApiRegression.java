package top.cnpcplus;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Mixin-shaped public additions on real CNPC wrappers, without constructing or invoking scripts. */
public final class AddonApiRegression {
    private static final String DATA = "Ltop/cnpcplus/AddonApiRegression$GeckoData;";

    public static final class GeckoData {
        public void setModel(String name) { throw new AssertionError("API was executed"); }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = new ClassLoader(AddonApiRegression.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!(name.startsWith("noppes.npcs.") || name.startsWith("top.cnpcplus.script.ScriptApiCompletion")
                        || name.startsWith("top.cnpcplus.script.ScriptLexical"))) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            ClassWriter writer = new ClassWriter(0);
                            new ClassReader(bytes).accept(writer, 0);
                            switch (name) {
                                case "noppes.npcs.api.wrapper.EntityLivingBaseWrapper" -> {
                                    add(writer, "castSpell", "(Ljava/lang/String;ILjava/lang/Object;)Z");
                                    add(writer, "isCastingSpell", "()Z");
                                    add(writer, "cancelSpell", "()Z");
                                }
                                case "noppes.npcs.api.wrapper.NPCWrapper" -> {
                                    add(writer, "syncAnimationsForAll", "(Ljava/lang/String;)V");
                                    add(writer, "getModelData", "()" + DATA);
                                    add(writer, "setGeckoModel", "(Ljava/lang/String;)V");
                                }
                                case "noppes.npcs.api.wrapper.BlockScriptedWrapper" ->
                                        add(writer, "setGeckoModel", "(Ljava/lang/String;)V");
                                case "noppes.npcs.api.wrapper.BlockWrapper",
                                        "noppes.npcs.api.wrapper.ItemScriptedWrapper" -> {
                                    add(writer, "castSpell", "(Ljava/lang/Object;Ljava/lang/String;ILjava/lang/Object;)Z");
                                    add(writer, "isCastingSpell", "(Ljava/lang/Object;)Z");
                                    add(writer, "cancelSpell", "(Ljava/lang/Object;)Z");
                                    if (name.endsWith("ItemScriptedWrapper")) add(writer, "setGeckoModel", "(Ljava/lang/String;)V");
                                }
                                case "noppes.npcs.api.wrapper.WrapperNpcAPI" ->
                                        add(writer, "createAnimBuilder", "()" + DATA);
                                default -> { }
                            }
                            bytes = writer.toByteArray();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException failure) {
                            throw new ClassNotFoundException(name, failure);
                        }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        Class<?> completion = loader.loadClass("top.cnpcplus.script.ScriptApiCompletion");
        Method complete = completion.getMethod("complete", String.class, int.class, String.class);
        Method hint = completion.getMethod("hint", String.class, int.class, String.class);
        for (String actor : List.of("npc", "player")) {
            String code = "function interact(e){ e." + actor + ".cast";
            check(names(entries(complete.invoke(null, code, code.length(), "NpcEvent"))).contains("castSpell"),
                    "inherited magic API missing from " + actor);
            code = "function interact(e){ e." + actor + ".castSpell(";
            check(signatures(entries(hint.invoke(null, code, code.length(), "NpcEvent"))).stream()
                    .anyMatch(s -> s.equals("castSpell(String, int, Object) : boolean")), "magic signature lost");
        }
        String code = "function interact(e){ e.npc.syncAnimationsForA";
        check(names(entries(complete.invoke(null, code, code.length(), "NpcEvent"))).contains("syncAnimationsForAll"),
                "NPC-only Gecko method missing");
        code = "function interact(e){ e.player.syncAnimationsForA";
        check(entries(complete.invoke(null, code, code.length(), "NpcEvent")).isEmpty(), "NPC API leaked into player");
        code = "function interact(e){ var d = e.npc.getModelData(); d.setM";
        check(names(entries(complete.invoke(null, code, code.length(), "NpcEvent"))).contains("setModel"),
                "addon return type/local chain lost");
        code = "function interact(e){ e.API.createAnimBuilder().setM";
        check(names(entries(complete.invoke(null, code, code.length(), "NpcEvent"))).contains("setModel"),
                "NpcAPI runtime wrapper/return chain lost");
        for (String context : List.of("BlockEvent", "ItemEvent")) {
            String field = context.equals("BlockEvent") ? "block" : "item";
            code = "function init(e){ var s = e." + field + "; s.setGeckoM";
            check(names(entries(complete.invoke(null, code, code.length(), context))).contains("setGeckoModel"),
                    "scripted wrapper API missing from " + context);
        }
        for (String context : List.of("BlockEvent", "BlockDoorEvent", "ItemEvent")) {
            String field = context.equals("ItemEvent") ? "item" : "block";
            code = "function init(e){ e." + field + ".castSpell(";
            check(signatures(entries(hint.invoke(null, code, code.length(), context))).stream()
                    .anyMatch(s -> s.equals("castSpell(Object, String, int, Object) : boolean")),
                    "magic carrier signature/inheritance lost in " + context);
        }
        code = "function interact(e){ e.npc.world.getBlock(1,2,3).castSpell(";
        check(signatures(entries(hint.invoke(null, code, code.length(), "NpcEvent"))).stream()
                .anyMatch(s -> s.equals("castSpell(Object, String, int, Object) : boolean")),
                "magic's generic BlockWrapper carrier was incorrectly hidden");
        code = "function interact(e){ var b = e.npc.world.getBlock(1,2,3); b.setGeckoM";
        check(entries(complete.invoke(null, code, code.length(), "NpcEvent")).isEmpty(),
                "scripted-only API leaked into a generic world block");
        System.out.println("ADDON_API_REGRESSION=PASS (transformed metadata only; no game/API execution)");
    }

    private static void add(ClassWriter writer, String name, String descriptor) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW, "java/lang/AssertionError");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V", false);
        method.visitInsn(Opcodes.ATHROW);
        method.visitMaxs(2, 5);
        method.visitEnd();
    }

    private static List<?> entries(Object result) throws Exception {
        return result instanceof List<?> list ? list : (List<?>) result.getClass().getField("entries").get(result);
    }

    private static List<String> names(List<?> entries) {
        return values(entries, "name");
    }

    private static List<String> signatures(List<?> entries) {
        return values(entries, "signature");
    }

    private static List<String> values(List<?> entries, String field) {
        return entries.stream().map(entry -> {
            try { return (String) entry.getClass().getField(field).get(entry); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }).toList();
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
