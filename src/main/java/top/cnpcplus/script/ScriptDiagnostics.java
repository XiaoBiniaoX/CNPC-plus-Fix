package top.cnpcplus.script;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.controllers.ScriptContainer;

/** Persists the latest runtime diagnostic through the native ScriptContainer NBT path. */
public final class ScriptDiagnostics {
    private static final String KEY = "CNPCPlusDiagnostic";
    private static final Map<ScriptContainer, ScriptDiagnostic> ERRORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ScriptDiagnostics() {}

    public static void capture(ScriptContainer owner, Throwable error) {
        if (owner != null && error != null) ERRORS.put(owner, ScriptDiagnostic.capture(error, owner.script));
    }

    public static ScriptDiagnostic get(ScriptContainer owner) {
        return owner == null ? null : ERRORS.get(owner);
    }

    public static ScriptDiagnostic checkStatic(ScriptContainer owner, String script, String language) {
        if (owner == null || language == null) return null;
        String name = language.toLowerCase(java.util.Locale.ROOT);
        if (!(name.contains("ecma") || name.contains("javascript") || name.contains("nashorn") || name.equals("js"))) return null;
        // Linked files can define globals; bracket checks remain safe without reading/executing them.
        return ScriptStaticCheck.check(script, ScriptContainer.Data.keySet(), owner.scripts.isEmpty());
    }

    public static void clear(ScriptContainer owner) {
        if (owner != null) ERRORS.remove(owner);
    }

    public static void clearRuntime(ScriptContainer owner) {
        clear(owner);
    }

    public static void read(ScriptContainer owner, CompoundTag nbt) {
        clear(owner);
        ScriptDiagnostic diagnostic = read(nbt, owner.script);
        if (diagnostic != null) ERRORS.put(owner, diagnostic);
    }

    public static ScriptDiagnostic read(CompoundTag nbt, String script) {
        if (!nbt.contains(KEY, Tag.TAG_COMPOUND)) return null;
        CompoundTag tag = nbt.getCompound(KEY);
        ScriptDiagnostic diagnostic = new ScriptDiagnostic(tag.getInt("Line"), tag.getInt("Column"),
                tag.getString("Hash"), tag.getString("Message"));
        return !diagnostic.message.startsWith("Static check:") && diagnostic.matches(script) ? diagnostic : null;
    }

    public static void write(ScriptContainer owner, CompoundTag nbt) {
        write(get(owner), owner.script, nbt);
    }

    public static void write(ScriptDiagnostic diagnostic, String script, CompoundTag nbt) {
        nbt.remove(KEY);
        if (diagnostic == null || diagnostic.message.startsWith("Static check:") || !diagnostic.matches(script)) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Line", diagnostic.line);
        tag.putInt("Column", diagnostic.column);
        tag.putString("Hash", diagnostic.hash);
        tag.putString("Message", diagnostic.message);
        nbt.put(KEY, tag);
    }
}
