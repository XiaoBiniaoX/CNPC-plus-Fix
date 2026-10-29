package bin.cnpcplus.script;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.ScriptContainer;

/** Attached to the existing ScriptContainer NBT and thus all native script data packets. */
public final class ScriptDiagnostics {
    private static final String KEY = "CNPCPlusDiagnostic";
    private static final Map<ScriptContainer, ScriptDiagnostic> ERRORS = Collections.synchronizedMap(new WeakHashMap<>());
    private ScriptDiagnostics() {}
    public static void capture(ScriptContainer owner, Throwable error) { ERRORS.put(owner, ScriptDiagnostic.capture(error, owner.script)); }
    public static ScriptDiagnostic get(ScriptContainer owner) { return owner.console.isEmpty() ? null : ERRORS.get(owner); }
    public static void clear(ScriptContainer owner) { ERRORS.remove(owner); }
    public static void read(ScriptContainer owner, NBTTagCompound nbt) {
        clear(owner);
        if (!nbt.hasKey(KEY, 10)) return;
        NBTTagCompound tag = nbt.getCompoundTag(KEY);
        ScriptDiagnostic diagnostic = new ScriptDiagnostic(tag.getInteger("Line"), tag.getInteger("Column"), tag.getString("Hash"), tag.getString("Message"));
        if (diagnostic.matches(owner.script)) ERRORS.put(owner, diagnostic);
    }
    public static void write(ScriptContainer owner, NBTTagCompound nbt) {
        nbt.removeTag(KEY);
        ScriptDiagnostic diagnostic = get(owner);
        if (diagnostic == null || !diagnostic.matches(owner.script)) return;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Line", diagnostic.line); tag.setInteger("Column", diagnostic.column);
        tag.setString("Hash", diagnostic.hash); tag.setString("Message", diagnostic.message);
        nbt.setTag(KEY, tag);
    }
}
