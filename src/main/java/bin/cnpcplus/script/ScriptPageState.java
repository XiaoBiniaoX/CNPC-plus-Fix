package bin.cnpcplus.script;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BooleanSupplier;
import javax.script.ScriptException;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.ScriptController;
import noppes.npcs.controllers.data.PlayerScriptData;
import top.cnpcplus.api.script.ApiText;
import top.cnpcplus.api.script.ScriptDiagnosticApi.RuntimeDiagnostic;
import top.cnpcplus.api.script.ScriptLanguageApi;
import top.cnpcplus.api.script.ScriptPageApi.ResetReason;
import top.cnpcplus.api.script.ScriptScopeApi.ScriptLifetime;

/** One state per native page. Only active executors/tokens are watched; owner references are weak. */
public final class ScriptPageState {
    public static final String LANGUAGE_KEY = "CNPCPlusLanguage", DIAGNOSTIC_KEY = "CNPCPlusRuntimeDiagnostic";
    private final WeakReference<ScriptContainer> page;
    private final WeakReference<IScriptHandler> handler;
    private String override = "", text, language;
    private List<String> links, linkedText;
    private ScriptController controller;
    private long loaded, playerUpdate, revision = 1;
    private boolean enabled, scriptingEnabled, reset, pendingLoad, pendingLegacy, externallyReported;
    private BooleanSupplier ownerAlive = () -> true;
    private RuntimeDiagnostic diagnostic;
    private String diagnosticHash = "";

    public ScriptPageState(ScriptContainer page, IScriptHandler handler) {
        this.page = new WeakReference<>(page); this.handler = new WeakReference<>(handler);
        remember();
    }
    public static ScriptPageState of(ScriptContainer page) {
        Objects.requireNonNull(page, "page");
        return ((ScriptPageAccess)(Object)page).cnpcplus$pageState();
    }
    public long revision() { return revision; }
    public boolean takeReset() { boolean value = reset; reset = false; return value; }
    public String languageOverride() { return override; }
    public String effectiveLanguage() {
        IScriptHandler owner = handler.get();
        return ScriptLanguageApi.resolve(override.isEmpty() ? owner == null ? "" : owner.getLanguage() : override);
    }
    public void setLanguage(String value) {
        String next = value.isEmpty() ? "" : ScriptLanguageApi.resolve(value);
        if (override.equals(next)) return;
        override = next;
        invalidate(ResetReason.LANGUAGE_CHANGED);
        remember();
    }
    private List<String> linkedText(ScriptContainer script) {
        var current = ScriptController.Instance;
        return script.scripts.stream().map(name -> current == null ? "" : current.scripts.getOrDefault(name, "")).toList();
    }
    private void remember() {
        var script = page.get(); var owner = handler.get();
        if (script == null || owner == null) return;
        text = script.script; links = List.copyOf(script.scripts); linkedText = linkedText(script); language = effectiveLanguage();
        controller = ScriptController.Instance;
        loaded = controller == null ? 0 : controller.lastLoaded;
        playerUpdate = controller == null ? 0 : controller.lastPlayerUpdate;
        enabled = owner.getEnabled();
        scriptingEnabled = CustomNpcs.EnableScripting;
    }
    /** Detect direct native field writes as well as public API edits before execution, capture, save and each active tick. */
    public void refresh() {
        var script = page.get(); var owner = handler.get();
        if (script == null || owner == null) return;
        // Native handlers deserialize their pages before their default language and enabled flag.
        if (pendingLoad) {
            remember(); pendingLoad = false;
            if (pendingLegacy && diagnostic != null) diagnosticHash = fingerprint();
            pendingLegacy = false;
        }
        var current = ScriptController.Instance;
        ResetReason reason = !Objects.equals(text, script.script) ? ResetReason.SOURCE_CHANGED
                : !links.equals(script.scripts) || !linkedText.equals(linkedText(script)) ? ResetReason.LINKS_CHANGED
                : !language.equals(effectiveLanguage()) ? ResetReason.LANGUAGE_CHANGED
                : controller != current || loaded != (current == null ? 0 : current.lastLoaded)
                    || owner instanceof PlayerScriptData && playerUpdate != (current == null ? 0 : current.lastPlayerUpdate) ? ResetReason.RELOAD
                : enabled != owner.getEnabled() || scriptingEnabled != CustomNpcs.EnableScripting ? ResetReason.EXPLICIT : null;
        if (reason != null) { invalidate(reason); remember(); }
        if (diagnostic != null && !diagnosticHash.equals(fingerprint())) diagnostic(null);
    }
    public void invalidate(ResetReason reason) {
        revision++;
        reset = true;
        ScriptLanguageRuntime.unwatch(this);
        var script = page.get();
        if (script != null) {
            ScriptPageAccess access = (ScriptPageAccess)(Object)script;
            var engine = access.cnpcplus$engine();
            access.cnpcplus$resetExecutor();
            if (engine != null) ScriptLanguageRuntime.close(engine);
        }
        if (reason == ResetReason.SOURCE_CHANGED || reason == ResetReason.LINKS_CHANGED || reason == ResetReason.LANGUAGE_CHANGED) diagnostic(null);
    }
    public void begin(BooleanSupplier ownerAlive) {
        refresh(); this.ownerAlive = ownerAlive; externallyReported = false;
        ScriptLanguageRuntime.watch(this);
    }
    public boolean ownerPresent() {
        var script = page.get(); var owner = handler.get();
        return script != null && owner != null && owner.getScripts().contains(script) && ownerAlive.getAsBoolean();
    }
    public ScriptLifetime lifetime() {
        refresh(); ScriptLanguageRuntime.watch(this);
        var weak = new WeakReference<>(this);
        long captured = revision;
        return new ScriptLifetime() {
            private boolean valid = true;
            @Override public boolean isValid() {
                if (!valid) return false;
                var state = weak.get();
                if (state == null) return valid = false;
                state.refresh();
                var script = state.page.get(); var owner = state.handler.get();
                valid = state.revision == captured && CustomNpcs.EnableScripting && script != null && !script.errored
                        && owner != null && owner.getEnabled() && (!(owner instanceof PlayerScriptData player) || player.isEnabled())
                        && state.ownerPresent();
                return valid;
            }
        };
    }
    private String fingerprint() {
        var script = page.get();
        if (script == null) return "";
        StringBuilder value = new StringBuilder();
        for (String part : List.of(script.script, effectiveLanguage())) value.append(part.length()).append(':').append(part);
        var bodies = linkedText(script);
        for (int i = 0; i < script.scripts.size(); i++) {
            String name = script.scripts.get(i), body = bodies.get(i);
            value.append(name.length()).append(':').append(name).append(body.length()).append(':').append(body);
        }
        return ScriptError.hash(value.toString());
    }
    public RuntimeDiagnostic diagnostic() { return diagnostic; }
    public void reportDiagnostic(RuntimeDiagnostic value) { diagnostic(value); externallyReported = true; }
    public void diagnostic(RuntimeDiagnostic value) {
        diagnostic = value; diagnosticHash = value == null ? "" : fingerprint();
        var script = page.get();
        if (value == null && script instanceof ScriptErrorAccess access) access.cnpcplus$clearError();
    }
    public void nativeError(Throwable failure) {
        if (externallyReported) return; // A language adapter already mapped this execution's generated source.
        var script = page.get();
        if (script == null) return;
        String source = "main";
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof ScriptException error) {
                String file = error.getFileName();
                if (file != null && !Set.of("<eval>", "<script>", "<stdin>").contains(file)) source = file;
                break;
            }
            if (cause == cause.getCause()) break;
        }
        var legacy = ScriptError.from(script.script, failure);
        int[] range = source.equals("main") && legacy != null ? legacy.range(script.script) : null;
        if (source.equals("main") && legacy != null && range == null) source = "linked";
        String message = failure.toString();
        diagnostic(new RuntimeDiagnostic(source, range == null ? -1 : range[0], range == null ? 0 : range[1] - range[0],
                ApiText.literal(message.substring(0, Math.min(512, message.length())))));
    }
    public void load(CompoundTag tag) {
        override = tag.getString(LANGUAGE_KEY);
        remember();
        pendingLoad = true; pendingLegacy = false;
        diagnostic = null;
        if (tag.contains(DIAGNOSTIC_KEY)) {
            CompoundTag saved = tag.getCompound(DIAGNOSTIC_KEY);
            String source = saved.getString("Source"), message = saved.getString("Message");
            int offset = saved.getInt("Offset"), length = saved.getInt("Length");
            if (!source.isBlank() && offset >= -1 && length >= 0 && (offset != -1 || length == 0)
                    && (!source.equals("main") || offset < 0 || offset <= text.length() && length <= text.length() - offset)) {
                List<String> arguments = new ArrayList<>();
                var list = saved.getList("Arguments", 8);
                for (int i = 0; i < list.size(); i++) arguments.add(list.getString(i));
                diagnostic = new RuntimeDiagnostic(source, offset, length, new ApiText(message, saved.getBoolean("Translatable"), arguments));
                diagnosticHash = saved.getString("Fingerprint");
            }
        } else {
            var old = ScriptError.load(tag.getCompound(ScriptError.KEY));
            if (old != null && old.matches(text)) {
                var range = old.range(text);
                diagnostic(new RuntimeDiagnostic(range == null ? "linked" : "main", range == null ? -1 : range[0],
                        range == null ? 0 : range[1] - range[0], ApiText.literal(old.message())));
                pendingLegacy = true;
            }
        }
    }
    public void save(CompoundTag tag) {
        refresh();
        if (override.isEmpty()) tag.remove(LANGUAGE_KEY); else tag.putString(LANGUAGE_KEY, override);
        if (diagnostic == null) { tag.remove(DIAGNOSTIC_KEY); return; }
        CompoundTag saved = new CompoundTag();
        saved.putString("Source", diagnostic.sourceId()); saved.putInt("Offset", diagnostic.offset()); saved.putInt("Length", diagnostic.length());
        saved.putString("Message", diagnostic.message().value()); saved.putBoolean("Translatable", diagnostic.message().translatable());
        var arguments = new net.minecraft.nbt.ListTag();
        diagnostic.message().arguments().forEach(value -> arguments.add(net.minecraft.nbt.StringTag.valueOf(value)));
        saved.put("Arguments", arguments); saved.putString("Fingerprint", diagnosticHash); tag.put(DIAGNOSTIC_KEY, saved);
    }
}
