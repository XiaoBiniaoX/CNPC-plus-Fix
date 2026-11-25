package bin.cnpcplus.script;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import net.neoforged.fml.ModList;
import org.objectweb.asm.*;

/** Shared provenance metadata; no GUI/client types, instances, or source evaluation. */
public final class ScriptHookMetadata {
    private static final String UNKNOWN = "?";
    private static Set<String> nativeHooks;
    private static final Map<String, String> hookSources = new HashMap<>();
    private static Map<String, List<String>> eventCandidates;
    private ScriptHookMetadata() {}

    public static synchronized String hookSource(String context, String hook) {
        if (!context.equals("ForgeEvent")) {
            if (nativeHooks == null) {
                nativeHooks = new HashSet<>();
                try (InputStream stream = ScriptHookMetadata.class.getResourceAsStream("/noppes/npcs/constants/EnumScriptType.class")) {
                    if (stream != null) new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                            if (!name.equals("<clinit>")) return null;
                            return new MethodVisitor(Opcodes.ASM9) {
                                private String lastString;
                                @Override public void visitLdcInsn(Object value) { if (value instanceof String text) lastString = text; }
                                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                                    if (owner.equals("noppes/npcs/constants/EnumScriptType") && name.equals("<init>")
                                            && descriptor.equals("(Ljava/lang/String;ILjava/lang/String;)V")) nativeHooks.add(lastString);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (IOException unavailable) { /* Cannot prove provenance without the original enum. */ }
            }
            return nativeHooks.contains(hook) ? "CNPC" : UNKNOWN;
        }
        return hookSources.computeIfAbsent(hook, name -> {
            ModList mods = ModList.get(); if (mods == null) return UNKNOWN;
            if (eventCandidates == null) {
                eventCandidates = new HashMap<>();
                for (var file : mods.getModFiles()) for (var metadata : file.getFile().getScanResult().getClasses()) {
                    String className = metadata.clazz().getClassName(), simple = className.substring(className.lastIndexOf('.') + 1).replace("$", "");
                    if (!simple.isEmpty()) eventCandidates.computeIfAbsent(Character.toLowerCase(simple.charAt(0)) + simple.substring(1),
                            ignored -> new ArrayList<>()).add(className);
                }
            }
            Set<String> origins = new HashSet<>();
            for (String className : eventCandidates.getOrDefault(name, List.of())) {
                try {
                    Class<?> type = Class.forName(className, false, ScriptHookMetadata.class.getClassLoader());
                    if (net.neoforged.bus.api.Event.class.isAssignableFrom(type)) origins.add(modSource(className));
                } catch (ClassNotFoundException | LinkageError unavailable) { /* Optional event metadata. */ }
            }
            return origins.size() == 1 ? origins.iterator().next() : UNKNOWN;
        });
    }

    public static String modSource(String name) {
        ModList mods = ModList.get(); if (mods == null) return UNKNOWN;
        String resource = name.replace('.', '/') + ".class", found = null;
        for (var info : mods.getModFiles()) {
            try {
                if (!java.nio.file.Files.isRegularFile(info.getFile().findResource(resource))) continue;
                String source = info.getMods().stream().map(mod -> mod.getDisplayName() + " [" + mod.getModId() + "]")
                        .collect(java.util.stream.Collectors.joining(" / "));
                if (source.isEmpty()) source = info.getFile().getFileName();
                if (found != null && !found.equals(source)) return UNKNOWN;
                found = source;
            } catch (RuntimeException unavailable) { /* Absent optional file. */ }
        }
        return found == null ? UNKNOWN : found;
    }
}
