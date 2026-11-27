package bin.cnpcplus.script;

import bin.cnpcplus.api.script.InvocationContext;
import bin.cnpcplus.api.script.LanguageDescriptor;
import bin.cnpcplus.api.script.PageContext;
import bin.cnpcplus.api.script.ScriptPage;
import bin.cnpcplus.api.script.ScriptRuntime;
import bin.cnpcplus.api.script.ScriptScope;
import bin.cnpcplus.api.script.ScriptHostApi;
import bin.cnpcplus.api.script.ScriptDiagnosticApi;
import bin.cnpcplus.api.script.SourceBundle;
import bin.cnpcplus.api.script.SourceUnit;
import bin.cnpcplus.script.tools.ScriptApiRegistry;
import bin.cnpcplus.script.tools.ScriptEvents;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.ScriptController;

/** CNPC 1.12.2 bridge for the public addon runtime contract. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptRuntimeHost {
    private static final String DIAGNOSTICS_NBT = "CNPCPlusRuntimeDiagnostics";
    private static final String SOURCE_REVISION_NBT = "CNPCPlusSourceRevision";
    private static final int MAX_DIAGNOSTICS = 16;
    private static final int MAX_SYNC_DIAGNOSTICS = 8;
    public interface PageAccess extends ScriptPage {
        String cnpcplus$language();
        void cnpcplus$language(String value);
        String cnpcplus$effectiveRawLanguage();
        String cnpcplus$effectiveLanguageId();
        long cnpcplus$revision();
        void cnpcplus$revision(long revision);
        long cnpcplus$generation();
        void cnpcplus$generation(long generation);
        String cnpcplus$fingerprint();
        void cnpcplus$fingerprint(String fingerprint);
        String cnpcplus$sourceFingerprint();
        void cnpcplus$sourceFingerprint(String fingerprint);
        IScriptHandler cnpcplus$handler();
        void cnpcplus$release();
    }

    private static final Map<ScriptContainer, State> STATES = Collections.synchronizedMap(new WeakHashMap<ScriptContainer, State>());
    private static final Map<ScriptContainer, String> MISSING = Collections.synchronizedMap(new WeakHashMap<ScriptContainer, String>());
    private static final Map<ScriptContainer, List<ScriptDiagnosticApi.Diagnostic>> PUBLIC_DIAGNOSTICS =
            Collections.synchronizedMap(new WeakHashMap<ScriptContainer, List<ScriptDiagnosticApi.Diagnostic>>());
    private static final ThreadLocal<Deque<InvocationContext>> CURRENT = ThreadLocal.withInitial(ArrayDeque::new);

    private ScriptRuntimeHost() {}

    public static boolean run(ScriptContainer page, String hook, Object event, Map<String, Object> nativeBindings) {
        PageAccess access = (PageAccess) page;
        String rawLanguage = access.cnpcplus$effectiveRawLanguage();
        java.util.Optional<String> runtimeId = ScriptHostApi.runtimeLanguageId(rawLanguage);
        boolean unavailableExplicit = ScriptHostApi.isExplicitLanguageId(rawLanguage)
                && !ScriptHostApi.language(rawLanguage).isPresent();
        if ((runtimeId.isPresent() || unavailableExplicit) && !serverThread()) return true;
        refresh(page, access, access.cnpcplus$effectiveLanguageId());
        if (!CustomNpcs.EnableScripting || !hasCode(page) || access.cnpcplus$handler() == null || !access.cnpcplus$handler().getEnabled()) {
            if (runtimeId.isPresent() || unavailableExplicit) {
                if (STATES.containsKey(page)) {
                    access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
                    release(page);
                } else MISSING.remove(page);
                return true;
            }
            releaseRuntime(page);
            return false;
        }
        if (runtimeId.isPresent()) {
            MISSING.remove(page);
            ScriptController controller = ScriptController.Instance;
            if (controller != null && controller.lastLoaded > page.lastCreated) {
                if (page.lastCreated != 0L) reload(page);
                page.lastCreated = controller.lastLoaded;
            }
            return invoke(page, access, hook, event, runtimeId.get(), nativeBindings);
        }
        if (unavailableExplicit) {
            if (STATES.containsKey(page)) {
                access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
                release(page);
            }
            if (!rawLanguage.equals(MISSING.get(page))) {
                MISSING.put(page, rawLanguage);
                page.appandConsole("Script language provider is unavailable: " + rawLanguage);
            }
            return true;
        }
        releaseRuntime(page);
        return false;
    }

    private static boolean invoke(ScriptContainer page, PageAccess access, String hook, Object event, String language, Map<String, Object> nativeBindings) {
        if (page.errored) return true;
        State state = STATES.get(page);
        if (state == null || !state.language.equals(language) || state.revision != access.cnpcplus$revision()) {
            if (state != null) release(page);
            state = new State(page, language, access.cnpcplus$revision(), access.cnpcplus$generation());
            STATES.put(page, state);
        }
        state.trackWorld(bin.cnpcplus.script.tools.ScriptScope.worldFor(event, access.cnpcplus$handler()));
        if (!state.scope.isAlive()) return true;
        if (state.missingHooks.contains(hook)) return true;
        return runInContext(page, access, state, hook, event, nativeBindings);
    }

    private static boolean runInContext(ScriptContainer page, PageAccess access, State state, String hook, Object event,
            Map<String, Object> nativeBindings) {
        Map<String, Object> globals = new LinkedHashMap<>(nativeBindings);
        ScriptApiRegistry.bind(globals);
        if (state.globals == null) state.globals = Collections.unmodifiableMap(new LinkedHashMap<>(globals));
        Map<String, Object> bindings = new LinkedHashMap<>(globals);
        bindings.put("event", event);
        PageContext pageContext = state.context;
        InvocationContext invocation = new InvocationContext(pageContext, InvocationContext.Kind.HOOK, hook, event, bindings);
        Deque<InvocationContext> stack = CURRENT.get();
        stack.push(invocation);
        ScriptContainer previous = ScriptContainer.Current;
        ScriptContainer.Current = page;
        long reportedBefore = state.diagnosticsVersion;
        try {
            if (state.runtime == null) {
                state.runtime = ScriptHostApi.createRuntime(state.language, pageContext);
                if (state.runtime == null) throw new IllegalStateException("RuntimeFactory returned null");
                state.runtime.load(access.source(), Collections.unmodifiableMap(globals));
            }
            boolean success = state.runtime.invoke(invocation);
            if (!success) state.missingHooks.add(hook);
            return true;
        } catch (Throwable error) {
            page.errored = true;
            state.close();
            state.scope.end();
            if (hasPreciseError(page, state, reportedBefore)) ScriptDiagnostics.clear(page);
            else {
                PUBLIC_DIAGNOSTICS.remove(page);
                ScriptDiagnostics.capture(page, error);
            }
            page.appandConsole(error.toString());
            bin.cnpcplus.CnpcPlus.LOGGER.error("[script] addon runtime failed for " + state.language, error);
            return true;
        } finally {
            ScriptContainer.Current = previous;
            stack.pop();
            if (stack.isEmpty()) CURRENT.remove();
        }
    }

    public static InvocationContext currentInvocation() {
        Deque<InvocationContext> stack = CURRENT.get();
        InvocationContext context = stack.peek();
        if (context == null) CURRENT.remove();
        return context;
    }

    public static void runCallback(ScriptScope scope, Runnable action) {
        if (!(scope instanceof Scope) || !scope.isAlive()) return;
        Scope live = (Scope) scope;
        State state = live.state;
        if (state == null || state.runtime == null) return;
        Map<String, Object> bindings = new LinkedHashMap<>(state.globals == null
                ? Collections.<String, Object>emptyMap() : state.globals);
        InvocationContext invocation = new InvocationContext(state.context, InvocationContext.Kind.CALLBACK, null, null, bindings);
        ScriptContainer page = state.page();
        if (page == null) return;
        Deque<InvocationContext> stack = CURRENT.get();
        stack.push(invocation);
        ScriptContainer previous = ScriptContainer.Current;
        ScriptContainer.Current = page;
        long reportedBefore = state.diagnosticsVersion;
        try { bin.cnpcplus.script.tools.ScriptScope.invokeCallback(state.callbackOwner, action); }
        catch (Throwable error) {
            page.errored = true;
            state.close();
            state.scope.end();
            if (hasPreciseError(page, state, reportedBefore)) ScriptDiagnostics.clear(page);
            else {
                PUBLIC_DIAGNOSTICS.remove(page);
                ScriptDiagnostics.capture(page, error);
            }
            page.appandConsole("script callback: " + error);
            if (error instanceof RuntimeException) throw (RuntimeException) error;
            throw new IllegalStateException(error);
        } finally {
            ScriptContainer.Current = previous;
            stack.pop();
            if (stack.isEmpty()) CURRENT.remove();
        }
    }

    public static void release(ScriptContainer page) {
        if (!serverThread()) return;
        releaseRuntime(page);
        MISSING.remove(page);
        PUBLIC_DIAGNOSTICS.remove(page);
        ScriptDiagnostics.clear(page);
        ScriptEvents.release(page);
    }

    public static void releaseRuntime(ScriptContainer page) {
        if (!serverThread()) return;
        State state = STATES.remove(page);
        if (state != null) state.close();
    }

    public static void release(IScriptHandler handler) {
        if (handler == null || !serverThread()) return;
        synchronized (STATES) {
            for (ScriptContainer page : new ArrayList<>(STATES.keySet()))
                if (page != null && ((PageAccess) page).cnpcplus$handler() == handler) release(page);
        }
    }

    public static void refresh(ScriptContainer page) {
        PageAccess access = (PageAccess) page;
        refresh(page, access, access.cnpcplus$effectiveLanguageId());
    }

    private static void refresh(ScriptContainer page, PageAccess access, String language) {
        if (!serverThread()) return;
        String source = sourceFingerprint(page);
        String runtime = runtimeFingerprint(language, source);
        String oldSource = access.cnpcplus$sourceFingerprint();
        String oldRuntime = access.cnpcplus$fingerprint();
        if (oldSource.isEmpty() && oldRuntime.isEmpty()) {
            access.cnpcplus$sourceFingerprint(source);
            access.cnpcplus$fingerprint(runtime);
        } else if (!source.equals(oldSource)) {
            access.cnpcplus$sourceFingerprint(source);
            access.cnpcplus$fingerprint(runtime);
            access.cnpcplus$revision(access.cnpcplus$revision() + 1L);
            access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
            page.errored = false;
            if (serverThread()) release(page);
        } else if (!runtime.equals(oldRuntime)) {
            access.cnpcplus$fingerprint(runtime);
            access.cnpcplus$revision(access.cnpcplus$revision() + 1L);
            access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
            page.errored = false;
            if (serverThread()) release(page);
        }
    }

    /** Invalidates callbacks immediately; non-server callers leave resource closure to the next server tick. */
    public static void languageUnregistered(String languageId) {
        if (languageId == null) return;
        synchronized (STATES) {
            for (Map.Entry<ScriptContainer, State> entry : new ArrayList<>(STATES.entrySet())) {
                ScriptContainer page = entry.getKey();
                State state = entry.getValue();
                if (page == null || state == null || !state.language.equals(languageId)) continue;
                state.scope.end();
                if (serverThread()) {
                    PageAccess access = (PageAccess) page;
                    access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
                    release(page);
                }
            }
        }
    }

    public static void setLanguageOverride(ScriptContainer page, String language) {
        if (!serverThread()) throw new IllegalStateException("Committed page language changes require the server thread");
        PageAccess access = (PageAccess) page;
        String value = language == null ? "" : language.trim();
        if (!value.isEmpty() && !ScriptHostApi.isExplicitLanguageId(value)) {
            java.util.Optional<LanguageDescriptor> descriptor = ScriptHostApi.language(value);
            if (!descriptor.isPresent()) throw new IllegalArgumentException("Unknown language: " + value);
            value = descriptor.get().id();
        }
        if (value.equals(access.cnpcplus$language())) return;
        refresh(page, access, access.cnpcplus$effectiveLanguageId());
        access.cnpcplus$language(value);
        access.cnpcplus$sourceFingerprint(sourceFingerprint(page));
        access.cnpcplus$fingerprint(runtimeFingerprint(access.cnpcplus$effectiveLanguageId(), access.cnpcplus$sourceFingerprint()));
        access.cnpcplus$revision(access.cnpcplus$revision() + 1L);
        access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
        release(page);
        page.errored = false;
    }

    public static void reload(ScriptContainer page) {
        if (!serverThread()) throw new IllegalStateException("Page reload requires the server thread");
        PageAccess access = (PageAccess) page;
        refresh(page, access, access.cnpcplus$effectiveLanguageId());
        access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
        release(page);
        page.errored = false;
    }

    public static void releaseAll() {
        synchronized (STATES) {
            for (Map.Entry<ScriptContainer, State> entry : new ArrayList<>(STATES.entrySet())) {
                entry.getValue().close();
                ScriptEvents.release(entry.getKey());
                ScriptDiagnostics.clear(entry.getKey());
            }
            STATES.clear();
            MISSING.clear();
            PUBLIC_DIAGNOSTICS.clear();
        }
        ScriptEvents.stop();
    }

    public static SourceBundle sourceSnapshot(ScriptContainer page) {
        refresh(page);
        return source(page, ((PageAccess) page).cnpcplus$revision());
    }

    public static boolean reportDiagnostic(ScriptScope publicScope, ScriptDiagnosticApi.Diagnostic diagnostic) {
        if (!serverThread() || !(publicScope instanceof Scope) || !publicScope.isAlive()) return false;
        Scope scope = (Scope) publicScope;
        State state = scope.state;
        ScriptContainer page = state.page();
        if (page == null || STATES.get(page) != state || diagnostic.revision() != state.revision
                || diagnostic.revision() != ((PageAccess) page).cnpcplus$revision()) return false;
        SourceUnit unit = findSource(source(page, state.revision), diagnostic.sourceId());
        if (unit == null || !unitHash(unit).equals(diagnostic.sourceHash())) return false;
        if (diagnostic.offset() < -1 || diagnostic.offset() >= 0
                && (diagnostic.offset() > unit.text().length() || diagnostic.length() > unit.text().length() - diagnostic.offset())) return false;
        List<ScriptDiagnosticApi.Diagnostic> current = new ArrayList<>(PUBLIC_DIAGNOSTICS.getOrDefault(page, Collections.<ScriptDiagnosticApi.Diagnostic>emptyList()));
        current.removeIf(old -> old.sourceId().equals(diagnostic.sourceId()) && old.code().equals(diagnostic.code()));
        if (current.size() >= MAX_DIAGNOSTICS) current.remove(0);
        current.add(diagnostic);
        PUBLIC_DIAGNOSTICS.put(page, Collections.unmodifiableList(current));
        state.diagnosticsVersion++;
        return true;
    }

    public static List<ScriptDiagnosticApi.Diagnostic> diagnostics(ScriptPage page) {
        ScriptContainer container = page instanceof PageHandle ? ((PageHandle) page).page.get()
                : page instanceof PageAccess ? (ScriptContainer) page : null;
        if (container == null) return Collections.emptyList();
        return PUBLIC_DIAGNOSTICS.getOrDefault(container, Collections.<ScriptDiagnosticApi.Diagnostic>emptyList());
    }

    private static boolean hasPreciseError(ScriptContainer page, State state, long reportedBefore) {
        if (state.diagnosticsVersion == reportedBefore) return false;
        for (ScriptDiagnosticApi.Diagnostic diagnostic : PUBLIC_DIAGNOSTICS.getOrDefault(
                page, Collections.<ScriptDiagnosticApi.Diagnostic>emptyList()))
            if (diagnostic.revision() == state.revision && diagnostic.severity() == ScriptDiagnosticApi.Severity.ERROR) return true;
        return false;
    }

    /** Stable current committed fingerprint, captured before native readFromNBT mutates a reused page. */
    public static String currentFingerprint(ScriptContainer page) {
        PageAccess access = (PageAccess) page;
        return runtimeFingerprint(access.cnpcplus$effectiveLanguageId(), sourceFingerprint(page));
    }

    public static void initializeSnapshot(ScriptContainer page, String previousFingerprint) {
        PageAccess access = (PageAccess) page;
        String source = sourceFingerprint(page);
        String runtime = runtimeFingerprint(access.cnpcplus$effectiveLanguageId(), source);
        String oldRuntime = access.cnpcplus$fingerprint();
        boolean initialized = !oldRuntime.isEmpty();
        boolean changed = initialized ? !runtime.equals(oldRuntime)
                : previousFingerprint != null && !previousFingerprint.equals(runtime);
        if (changed) {
            page.errored = false;
            ScriptDiagnostics.clear(page);
            PUBLIC_DIAGNOSTICS.remove(page);
        }
        if (initialized) {
            if (changed) access.cnpcplus$revision(access.cnpcplus$revision() + 1L);
            access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
        }
        access.cnpcplus$sourceFingerprint(source);
        access.cnpcplus$fingerprint(runtime);
    }

    /** Runtime diagnostic fields use the existing page NBT sync path; Throwable is intentionally omitted. */
    public static void writeDiagnostics(ScriptContainer page, NBTTagCompound nbt) {
        nbt.removeTag(DIAGNOSTICS_NBT);
        nbt.removeTag(SOURCE_REVISION_NBT);
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getEffectiveSide().isClient()) return;
        PageAccess access = (PageAccess) page;
        nbt.setLong(SOURCE_REVISION_NBT, access.cnpcplus$revision());
        List<ScriptDiagnosticApi.Diagnostic> values = PUBLIC_DIAGNOSTICS.get(page);
        if (values == null || values.isEmpty()) return;
        SourceBundle current = source(page, access.cnpcplus$revision());
        NBTTagList list = new NBTTagList();
        for (int index = Math.max(0, values.size() - MAX_SYNC_DIAGNOSTICS); index < values.size(); index++) {
            ScriptDiagnosticApi.Diagnostic diagnostic = values.get(index);
            if (!isCurrentDiagnostic(diagnostic, current, access.cnpcplus$revision())) continue;
            NBTTagCompound item = new NBTTagCompound();
            item.setString("SourceId", diagnostic.sourceId()); item.setLong("Revision", diagnostic.revision());
            item.setString("SourceHash", diagnostic.sourceHash()); item.setString("Severity", diagnostic.severity().name());
            item.setString("Code", diagnostic.code()); item.setString("Message", diagnostic.message());
            item.setString("TranslationKey", diagnostic.translationKey());
            item.setInteger("Offset", diagnostic.offset()); item.setInteger("Length", diagnostic.length());
            item.setInteger("Line", diagnostic.line()); item.setInteger("Column", diagnostic.column());
            NBTTagList arguments = new NBTTagList();
            for (String argument : diagnostic.arguments()) arguments.appendTag(new NBTTagString(argument));
            item.setTag("Arguments", arguments);
            list.appendTag(item);
            if (list.tagCount() == MAX_SYNC_DIAGNOSTICS) break;
        }
        if (list.tagCount() > 0) {
            nbt.setTag(DIAGNOSTICS_NBT, list);
        }
    }

    /** Only the client consumes these records; the server never trusts a client-provided diagnostic snapshot. */
    public static void readDiagnostics(ScriptContainer page, NBTTagCompound nbt) {
        if (!net.minecraftforge.fml.common.FMLCommonHandler.instance().getEffectiveSide().isClient()) return;
        PUBLIC_DIAGNOSTICS.remove(page);
        PageAccess access = (PageAccess) page;
        if (nbt.hasKey(SOURCE_REVISION_NBT, 99)) access.cnpcplus$revision(nbt.getLong(SOURCE_REVISION_NBT));
        else access.cnpcplus$revision(0L);
        if (!nbt.hasKey(DIAGNOSTICS_NBT, 9)) return;
        SourceBundle current = source(page, access.cnpcplus$revision());
        NBTTagList list = nbt.getTagList(DIAGNOSTICS_NBT, 10);
        List<ScriptDiagnosticApi.Diagnostic> loaded = new ArrayList<>();
        for (int i = 0; i < Math.min(list.tagCount(), MAX_DIAGNOSTICS); i++) {
            NBTTagCompound item = list.getCompoundTagAt(i);
            ScriptDiagnosticApi.Severity severity = severity(item.getString("Severity"));
            if (severity == null) continue;
            NBTTagList argsTag = item.getTagList("Arguments", 8);
            List<String> arguments = new ArrayList<>();
            for (int j = 0; j < Math.min(argsTag.tagCount(), 8); j++) arguments.add(argsTag.getStringTagAt(j));
            try {
                ScriptDiagnosticApi.Diagnostic diagnostic = new ScriptDiagnosticApi.Diagnostic(
                        item.getString("SourceId"), item.getLong("Revision"), item.getString("SourceHash"), severity,
                        item.getString("Code"), item.getString("Message"), item.getString("TranslationKey"), arguments,
                        item.getInteger("Offset"), item.getInteger("Length"), item.getInteger("Line"), item.getInteger("Column"), null);
                if (isCurrentDiagnostic(diagnostic, current, access.cnpcplus$revision())) loaded.add(diagnostic);
            } catch (IllegalArgumentException ignored) { /* Malformed NBT diagnostic is skipped at the input boundary. */ }
        }
        if (!loaded.isEmpty()) PUBLIC_DIAGNOSTICS.put(page, Collections.unmodifiableList(loaded));
    }

    private static ScriptDiagnosticApi.Severity severity(String name) {
        for (ScriptDiagnosticApi.Severity value : ScriptDiagnosticApi.Severity.values()) if (value.name().equals(name)) return value;
        return null;
    }

    private static boolean isCurrentDiagnostic(ScriptDiagnosticApi.Diagnostic diagnostic, SourceBundle source, long revision) {
        if (diagnostic.revision() != revision) return false;
        SourceUnit unit = findSource(source, diagnostic.sourceId());
        if (unit == null || !unitHash(unit).equals(diagnostic.sourceHash())) return false;
        return diagnostic.offset() < 0 ? diagnostic.length() == 0
                : diagnostic.offset() <= unit.text().length()
                && diagnostic.length() <= unit.text().length() - diagnostic.offset();
    }

    public static String contextKind(ScriptContainer page) { return contextKind(((PageAccess) page).cnpcplus$handler()); }
    public static boolean isPresent(ScriptContainer page) {
        IScriptHandler handler = ((PageAccess) page).cnpcplus$handler();
        return handler != null && handler.getScripts().contains(page);
    }
    public static String normalizeStoredOverride(String value) {
        if (value == null || value.isEmpty()) return "";
        return ScriptHostApi.language(value).map(LanguageDescriptor::id).orElse(value);
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void serverTick(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END) return;
        synchronized (STATES) {
            for (ScriptContainer page : new ArrayList<>(STATES.keySet())) {
                State state = page == null ? null : STATES.get(page);
                if (state == null) continue;
                PageAccess access = (PageAccess) page;
                refresh(page, access, access.cnpcplus$effectiveLanguageId());
                state = STATES.get(page);
                if (state == null) continue;
                if (access.cnpcplus$handler() == null || !CustomNpcs.EnableScripting || !access.cnpcplus$handler().getEnabled()
                        || !ScriptRuntimeHost.isPresent(page) || !state.scope.isAlive()) {
                    access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
                    release(page);
                }
            }
        }
    }

    private static SourceBundle source(ScriptContainer page, long revision) {
        List<SourceUnit> linked = new ArrayList<>();
        ScriptController controller = ScriptController.Instance;
        if (controller != null) for (String path : page.scripts) {
            String text = controller.scripts.get(path);
            if (text != null) linked.add(new SourceUnit(path, path, text));
        }
        return new SourceBundle(new SourceUnit("page", "Script", page.script), linked, revision);
    }

    private static String sourceFingerprint(ScriptContainer page) {
        StringBuilder value = new StringBuilder(SourceUnit.normalizeText(page.script));
        ScriptController controller = ScriptController.Instance;
        for (String path : page.scripts) value.append('\u0000').append(path).append('\u0000')
                .append(controller == null ? "" : SourceUnit.normalizeText(controller.scripts.get(path)));
        return ScriptDiagnostic.hash(value.toString());
    }

    private static SourceUnit findSource(SourceBundle source, String sourceId) {
        if (source.main().sourceId().equals(sourceId)) return source.main();
        for (SourceUnit unit : source.linked()) if (unit.sourceId().equals(sourceId)) return unit;
        return null;
    }

    private static String unitHash(SourceUnit unit) { return ScriptDiagnostic.hash(unit.text()); }

    private static String runtimeFingerprint(String language, String sourceFingerprint) {
        return ScriptDiagnostic.hash(language + '\u0000' + sourceFingerprint);
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void worldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (!(event.getWorld() instanceof net.minecraft.world.WorldServer) || !serverThread()) return;
        synchronized (STATES) {
            for (ScriptContainer page : new ArrayList<>(STATES.keySet())) {
                State state = page == null ? null : STATES.get(page);
                if (state != null && (state.worlds.contains((net.minecraft.world.WorldServer) event.getWorld())
                        || !state.scope.isAlive())) {
                    PageAccess access = (PageAccess) page;
                    access.cnpcplus$generation(access.cnpcplus$generation() + 1L);
                    release(page);
                }
            }
        }
    }

    private static String contextKind(IScriptHandler handler) {
        String name = handler == null ? "" : handler.getClass().getName();
        if (name.contains("PlayerScriptData")) return "player";
        if (name.contains("ForgeScriptData")) return "forge";
        if (name.contains("Item")) return "item";
        if (name.contains("Block") || name.contains("Door")) return name.contains("Door") ? "door" : "block";
        return "npc";
    }

    private static boolean hasCode(ScriptContainer page) {
        if (page.script != null && !page.script.isEmpty()) return true;
        ScriptController controller = ScriptController.Instance;
        if (controller == null) return false;
        for (String path : page.scripts) {
            String text = controller.scripts.get(path);
            if (text != null && !text.isEmpty()) return true;
        }
        return false;
    }

    private static final class State {
        final java.lang.ref.WeakReference<ScriptContainer> page;
        final String language;
        final long revision, generation;
        final Scope scope;
        final PageContext context;
        final BooleanSupplier callbackOwner;
        final Set<String> missingHooks = new HashSet<>();
        final Set<net.minecraft.world.WorldServer> worlds = Collections.newSetFromMap(
                new WeakHashMap<net.minecraft.world.WorldServer, Boolean>());
        Map<String, Object> globals;
        ScriptRuntime runtime;
        long diagnosticsVersion;

        State(ScriptContainer page, String language, long revision, long generation) {
            this.page = new java.lang.ref.WeakReference<>(page); this.language = language; this.revision = revision; this.generation = generation;
            this.scope = new Scope(this);
            ScriptPage handle = new PageHandle(this.page);
            this.context = new PageContext(handle, scope, new Output(this, false), new Output(this, true), ScriptRuntimeHost.class.getClassLoader());
            this.callbackOwner = bin.cnpcplus.script.tools.ScriptScope.withCurrentLifetime(scope);
        }

        ScriptContainer page() { return page.get(); }
        void trackWorld(net.minecraft.world.WorldServer world) { if (world != null) worlds.add(world); }

        void close() {
            scope.end();
            for (int i = scope.resources.size() - 1; i >= 0; i--) {
                try { scope.resources.get(i).close(); }
                catch (Throwable error) { bin.cnpcplus.CnpcPlus.LOGGER.warn("Script resource close failed", error); }
            }
            scope.resources.clear();
            worlds.clear();
            globals = null;
            ScriptRuntime old = runtime; runtime = null;
            if (old != null) try { old.close(); } catch (Throwable error) { bin.cnpcplus.CnpcPlus.LOGGER.warn("Script runtime close failed", error); }
            missingHooks.clear();
        }
    }

    private static final class Scope implements ScriptScope, BooleanSupplier {
        final State state;
        final BooleanSupplier ownerAlive;
        final List<AutoCloseable> resources = new ArrayList<>();
        volatile boolean alive = true;
        Scope(State state) { this.state = state; this.ownerAlive = bin.cnpcplus.script.tools.ScriptScope.currentLifetime(); }
        @Override public boolean getAsBoolean() { return isAlive(); }
        @Override public boolean isAlive() {
            if (!alive) return false;
            ScriptContainer page = state.page();
            if (page == null || STATES.get(page) != state || state.generation != ((PageAccess)page).cnpcplus$generation()) {
                alive = false;
                return false;
            }
            if (!ScriptHostApi.runtimeLanguageId(state.language).isPresent()) {
                alive = false;
                return false;
            }
            if (!serverThread()) return true;
            if (!ScriptRuntimeHost.isPresent(page) || !ownerAlive.getAsBoolean()) alive = false;
            return alive;
        }
        @Override public void manage(AutoCloseable resource) {
            if (!isAlive()) throw new IllegalStateException("Script scope is no longer active");
            if (!serverThread()) throw new IllegalStateException("Resources must be managed on the server thread");
            resources.add(java.util.Objects.requireNonNull(resource, "resource"));
        }
        void end() { alive = false; }
    }

    private static final class Output extends Writer {
        final State state; final boolean error;
        Output(State state, boolean error) { this.state = state; this.error = error; }
        @Override public void write(char[] chars, int offset, int length) {
            if (!serverThread() || !state.scope.isAlive() || length == 0) return;
            String text = new String(chars, offset, length);
            ScriptContainer page = state.page();
            if (page != null) page.appandConsole(text);
        }
        @Override public void flush() {}
        @Override public void close() {}
    }

    private static boolean serverThread() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        return server != null && server.isCallingFromMinecraftThread();
    }

    private static final class PageHandle implements ScriptPage {
        private final java.lang.ref.WeakReference<ScriptContainer> page;
        PageHandle(java.lang.ref.WeakReference<ScriptContainer> page) { this.page = page; }
        private ScriptContainer get() {
            ScriptContainer value = page.get();
            if (value == null) throw new IllegalStateException("Script page no longer exists");
            return value;
        }
        @Override public String contextKind() { return ScriptRuntimeHost.contextKind(get()); }
        @Override public String languageOverride() { return ((PageAccess)get()).cnpcplus$language(); }
        @Override public String effectiveLanguageId() { return ((PageAccess)get()).cnpcplus$effectiveLanguageId(); }
        @Override public SourceBundle source() { return ScriptRuntimeHost.sourceSnapshot(get()); }
        @Override public long generation() { return ((PageAccess)get()).generation(); }
        @Override public boolean isPresent() { return ScriptRuntimeHost.isPresent(get()); }
        @Override public void setLanguageOverride(String languageIdOrEmpty) { ScriptRuntimeHost.setLanguageOverride(get(), languageIdOrEmpty); }
        @Override public void reload() { ScriptRuntimeHost.reload(get()); }
    }
}
