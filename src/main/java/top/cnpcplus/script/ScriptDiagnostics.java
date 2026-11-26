package top.cnpcplus.script;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public record StaticReport(List<ScriptDiagnostic> findings,
                               List<top.cnpcplus.api.script.ScriptCheckApi.CheckRun> providers,
                               boolean builtinChecked) {
        public StaticReport {
            findings = List.copyOf(findings);
            providers = List.copyOf(providers);
        }
    }

    public static void capture(ScriptContainer owner, Throwable error) {
        if (owner != null && error != null) ERRORS.put(owner, ScriptDiagnostic.capture(error, owner.script));
    }

    public static ScriptDiagnostic get(ScriptContainer owner) {
        return owner == null ? null : ERRORS.get(owner);
    }

    public static ScriptDiagnostic checkStatic(ScriptContainer owner, String script, String language) {
        List<ScriptDiagnostic> findings = checkStaticAll(owner, script, language, Set.of());
        return findings.stream().filter(item -> item.severity != ScriptDiagnostic.Severity.INFO).findFirst().orElse(null);
    }

    public static List<ScriptDiagnostic> checkStaticAll(ScriptContainer owner, String script, String language,
                                                         Set<String> hooks) {
        return checkStaticReport(owner, script, language, hooks).findings();
    }

    public static StaticReport checkStaticReport(ScriptContainer owner, String script, String language,
                                                 Set<String> hooks) {
        Set<String> globals = new HashSet<>(ScriptContainer.Data.keySet());
        globals.addAll(top.cnpcplus.script.tools.ScriptApiRegistry.roots().keySet());
        globals.addAll(top.cnpcplus.api.script.ScriptCheckApi.registeredGlobals());
        boolean builtin = top.cnpcplus.api.script.ScriptCompletionApi.usesBuiltinChecks(language);
        List<ScriptDiagnostic> findings = new java.util.ArrayList<>(builtin
                ? ScriptStaticCheck.checkAll(script, globals, owner == null || owner.scripts.isEmpty(), hooks) : List.of());
        var source = new top.cnpcplus.api.script.ScriptCheckApi.Source(
                script == null ? "" : script, language, Set.copyOf(globals));
        var providerRuns = top.cnpcplus.api.script.ScriptCheckApi.checkWithStatus(source);
        for (var run : providerRuns) for (var finding : run.findings()) {
            int offset = Math.min(source.text().length(), finding.offset());
            int line = 1, start = 0;
            for (int i = 0; i < offset; i++) if (source.text().charAt(i) == '\n') { line++; start = i + 1; }
            ScriptDiagnostic.Severity severity = switch (finding.severity()) {
                case ERROR -> ScriptDiagnostic.Severity.ERROR;
                case WARN -> ScriptDiagnostic.Severity.WARN;
                case INFO -> ScriptDiagnostic.Severity.INFO;
            };
            findings.add(new ScriptDiagnostic(line, offset - start + 1, ScriptDiagnostic.hash(source.text()),
                    "Addon self-check: " + finding.subject(), finding.translationKey(), finding.subject(),
                    Math.min(finding.length(), source.text().length() - offset), severity));
        }
        findings.sort(java.util.Comparator.comparingInt((ScriptDiagnostic item) -> item.severity.ordinal())
                .thenComparingInt(item -> offset(script == null ? "" : script, item.line, item.column)));
        return new StaticReport(findings, providerRuns, builtin);
    }

    private static int offset(String source, int line, int column) {
        int start = 0;
        for (int current = 1; current < line; current++) {
            int end = source.indexOf('\n', start);
            if (end < 0) return source.length();
            start = end + 1;
        }
        return Math.min(source.length(), start + Math.max(0, column - 1));
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
        return !diagnostic.isStaticCheck() && diagnostic.matches(script) ? diagnostic : null;
    }

    public static void write(ScriptContainer owner, CompoundTag nbt) {
        write(get(owner), owner.script, nbt);
    }

    public static void write(ScriptDiagnostic diagnostic, String script, CompoundTag nbt) {
        nbt.remove(KEY);
        if (diagnostic == null || diagnostic.isStaticCheck() || !diagnostic.matches(script)) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("Line", diagnostic.line);
        tag.putInt("Column", diagnostic.column);
        tag.putString("Hash", diagnostic.hash);
        tag.putString("Message", diagnostic.message);
        nbt.put(KEY, tag);
    }
}
