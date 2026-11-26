package top.cnpcplus.api.script;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.ScriptController;
import noppes.npcs.entity.data.DataScript;
import noppes.npcs.controllers.data.ForgeScriptData;
import noppes.npcs.controllers.data.PlayerScriptData;
import noppes.npcs.blocks.tiles.TileScripted;
import noppes.npcs.blocks.tiles.TileScriptedDoor;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Public, immutable snapshots of a loaded script page and its source text. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptPageApi {
    public enum Carrier { NPC, PLAYER, BLOCK, BLOCK_DOOR, ITEM, FORGE, UNKNOWN }

    public enum DisposeReason {
        SOURCE_CHANGED, LANGUAGE_CHANGED, DISABLED, ENABLED, REMOVED, RELOADED,
        OWNER_UNAVAILABLE, SERVER_STOPPING
    }

    public static final class PageRef {
        private final String id = UUID.randomUUID().toString();
        private final WeakReference<ScriptContainer> page;
        private final WeakReference<IScriptHandler> handler;
        private final boolean serverOwned;
        private volatile boolean stale;
        private volatile long revision = 1;

        private PageRef(ScriptContainer page, IScriptHandler handler) {
            this.page = new WeakReference<>(page);
            this.handler = new WeakReference<>(handler);
            var server = ServerLifecycleHooks.getCurrentServer();
            serverOwned = server != null && server.isSameThread();
        }

        /** Opaque process-local identity; do not persist or send it over the network. */
        public String id() { return id; }
    }

    public record HookDescriptor(String name, Carrier carrier,
                                 Optional<String> sourceTypeName, Optional<String> binaryTypeName) {
        public HookDescriptor {
            sourceTypeName = sourceTypeName == null ? Optional.empty() : sourceTypeName;
            binaryTypeName = binaryTypeName == null ? Optional.empty() : binaryTypeName;
        }
    }

    public record PageContext(PageRef page, Carrier carrier, String defaultLanguage,
                              String overrideLanguage, String effectiveLanguage,
                              long revision, List<HookDescriptor> hooks) {
        public PageContext { hooks = List.copyOf(hooks); }
    }

    /** Editor-side applied configuration metadata; intentionally carries no runtime PageRef. */
    public record EditorDescriptor(Carrier carrier, String defaultLanguage, String overrideLanguage,
                                   String effectiveLanguage, List<HookDescriptor> hooks) {
        public EditorDescriptor { hooks = List.copyOf(hooks); }
    }

    public record SourceUnit(String sourceId, String displayName, String text, boolean main) {}
    public record SourceProblem(String sourceId, String displayName, String messageKey) {}
    public record SourceBundle(String documentId, long documentRevision,
                               List<SourceUnit> units, List<SourceProblem> problems) {
        public SourceBundle {
            units = List.copyOf(units);
            problems = List.copyOf(problems);
        }
    }

    public static final class PageUnavailableException extends IllegalStateException {
        public PageUnavailableException() { super("cnpcplus.script.page_unavailable"); }
    }

    public static final class LanguageConflictException extends IllegalStateException {
        private final List<String> providerIds;
        private final List<String> candidates;

        private LanguageConflictException(List<String> providerIds, List<String> candidates) {
            super("cnpcplus.script.language_conflict");
            this.providerIds = List.copyOf(providerIds);
            this.candidates = List.copyOf(candidates);
        }

        public List<String> providerIds() { return providerIds; }
        public List<String> candidates() { return candidates; }
    }

    @FunctionalInterface
    public interface LanguageResolver {
        Optional<String> overrideLanguage(ScriptContainer page, IScriptHandler handler);
    }

    private record Resolver(String id, LanguageResolver provider) {}

    private static final class State {
        private final PageRef ref;
        private final WeakReference<IScriptHandler> handler;
        private String language;
        private String overrideLanguage;
        private List<String> linked;
        private Map<String, String> linkedContents;
        private String source;
        private boolean enabled;
        private boolean conflicted;
        private long loadedGeneration;
        private long playerUpdateGeneration;
        private long revision = 1;
        private long documentRevision = 1;
        private final String documentId = UUID.randomUUID().toString();

        private State(ScriptContainer page, IScriptHandler handler, String language, String overrideLanguage) {
            ref = new PageRef(page, handler);
            this.handler = new WeakReference<>(handler);
            this.language = language;
            this.overrideLanguage = overrideLanguage;
            linked = List.copyOf(page.scripts);
            linkedContents = linkedSources(page);
            source = safe(page.script);
            enabled = runtimeEnabled(handler, ref.serverOwned);
            ScriptController controller = ScriptController.Instance;
            loadedGeneration = controller == null ? 0 : controller.lastLoaded;
            playerUpdateGeneration = controller == null || !(handler instanceof PlayerScriptData)
                    ? 0 : controller.lastPlayerUpdate;
        }
    }

    private static final Map<ScriptContainer, State> STATES = new WeakHashMap<>();
    private static volatile List<Resolver> RESOLVERS = List.of();

    private ScriptPageApi() {}

    /** Describe a loaded page using its CNPC handler carrier and the supplied raw hook names. */
    public static PageContext describe(ScriptContainer page, IScriptHandler handler, List<String> hookNames) {
        return describe(page, handler, carrier(handler), hookNames);
    }

    /** Host overload for carriers that cannot be inferred from a CNPC handler implementation. */
    public static synchronized PageContext describe(ScriptContainer page, IScriptHandler handler,
                                                    Carrier carrier, List<String> hookNames) {
        requireLoaded(page, handler);
        State state = STATES.get(page);
        if (state == null || state.handler.get() != handler) {
            if (state != null) {
                state.ref.stale = true;
                state.revision++;
                state.ref.revision = state.revision;
                ScriptLifecycleApi.invalidate(state.ref, DisposeReason.RELOADED);
            }
            Resolution resolution = resolve(page, handler);
            state = new State(page, handler, resolution.effective, resolution.override);
            STATES.put(page, state);
        } else {
            refresh(page, handler, state);
        }
        Carrier actualCarrier = carrier == null ? Carrier.UNKNOWN : carrier;
        return new PageContext(state.ref, actualCarrier, safe(handler.getLanguage()), state.overrideLanguage, state.language,
                state.revision, hooks(hookNames, actualCarrier));
    }

    public static PageContext describe(ScriptContainer page, IScriptHandler handler) {
        return describe(page, handler, List.of());
    }

    /** Resolve editor metadata without creating a runtime lifetime handle or mutating page revisions. */
    public static EditorDescriptor describeEditor(ScriptContainer page, IScriptHandler handler,
                                                  Carrier carrier, List<String> hookNames) {
        requireLoaded(page, handler);
        Carrier actualCarrier = carrier == null ? Carrier.UNKNOWN : carrier;
        Resolution resolution = resolve(page, handler);
        return new EditorDescriptor(actualCarrier, safe(handler.getLanguage()), resolution.override,
                resolution.effective, hooks(hookNames, actualCarrier));
    }

    /** Create a source snapshot from the editor's current draft, separate from applied runtime revisions. */
    public static SourceBundle draftSources(ScriptContainer page, IScriptHandler handler,
                                            String documentId, long documentRevision, String mainText) {
        requireLoaded(page, handler);
        if (documentId == null || documentId.isBlank()) throw new IllegalArgumentException("documentId");
        var units = new ArrayList<SourceUnit>();
        var problems = new ArrayList<SourceProblem>();
        units.add(new SourceUnit("main", "main", safe(mainText), true));
        Map<String, String> linkedSources = linkedSources(page);
        var occurrences = new java.util.HashMap<String, Integer>();
        for (String name : page.scripts) {
            int occurrence = occurrences.merge(name, 1, Integer::sum);
            String id = "linked:" + name + (occurrence == 1 ? "" : "#" + occurrence);
            String text = linkedSources.get(name);
            if (text == null) problems.add(new SourceProblem(id, name, "cnpcplus.script.source_missing"));
            else units.add(new SourceUnit(id, name, text, false));
        }
        return new SourceBundle(documentId, documentRevision, units, problems);
    }

    /** Return the exact source snapshot currently applied to the loaded page. */
    public static synchronized SourceBundle sources(PageRef ref) {
        State state = state(ref);
        ScriptContainer page = ref.page.get();
        IScriptHandler handler = ref.handler.get();
        observe(ref);
        state = state(ref);
        if (page == null || handler == null || !handler.getScripts().contains(page)) throw new PageUnavailableException();
        var units = new ArrayList<SourceUnit>();
        var problems = new ArrayList<SourceProblem>();
        units.add(new SourceUnit("main", "main", safe(page.script), true));
        Map<String, String> linkedSources = state.linkedContents;
        var occurrences = new java.util.HashMap<String, Integer>();
        for (String name : page.scripts) {
            int occurrence = occurrences.merge(name, 1, Integer::sum);
            String id = "linked:" + name + (occurrence == 1 ? "" : "#" + occurrence);
            String text = linkedSources.get(name);
            if (text == null) problems.add(new SourceProblem(id, name, "cnpcplus.script.source_missing"));
            else units.add(new SourceUnit(id, name, text, false));
        }
        return new SourceBundle(state.documentId, state.documentRevision, units, problems);
    }

    /** Mark one applied configuration change. Repeated/stale references are ignored. */
    public static synchronized void invalidate(PageRef ref, DisposeReason reason) {
        State state = stateOrNull(ref);
        if (state == null) return;
        ScriptContainer page = ref.page.get();
        IScriptHandler handler = ref.handler.get();
        if (page == null || handler == null || !handler.getScripts().contains(page)) return;
        boolean changedSource = !state.source.equals(safe(page.script)) || !state.linked.equals(page.scripts)
                || !state.linkedContents.equals(linkedSources(page));
        state.source = safe(page.script);
        state.linked = List.copyOf(page.scripts);
        state.linkedContents = linkedSources(page);
        try {
            Resolution resolution = resolve(page, handler);
            state.language = resolution.effective;
            state.overrideLanguage = resolution.override;
            state.conflicted = false;
        } catch (LanguageConflictException conflict) {
            state.conflicted = true;
        }
        state.enabled = runtimeEnabled(handler, ref.serverOwned);
        ScriptController controller = ScriptController.Instance;
        state.loadedGeneration = controller == null ? 0 : controller.lastLoaded;
        state.playerUpdateGeneration = controller == null || !(handler instanceof PlayerScriptData)
                ? 0 : controller.lastPlayerUpdate;
        state.revision++;
        ref.revision = state.revision;
        if (changedSource) state.documentRevision++;
        ScriptLifecycleApi.invalidate(ref, reason == null ? DisposeReason.RELOADED : reason);
    }

    /** Register page-language settings without changing the stored CNPC language field. */
    public static synchronized void registerLanguageResolver(String id, LanguageResolver resolver) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_.-]+") || resolver == null)
            throw new IllegalArgumentException("id/resolver");
        var updated = new ArrayList<>(RESOLVERS);
        updated.removeIf(entry -> entry.id.equals(id));
        updated.add(new Resolver(id, resolver));
        updated.sort(java.util.Comparator.comparing(Resolver::id));
        RESOLVERS = List.copyOf(updated);
    }

    public static synchronized void unregisterLanguageResolver(String id) {
        RESOLVERS = RESOLVERS.stream().filter(entry -> !entry.id.equals(id)).toList();
    }

    static synchronized long currentRevision(PageRef ref) {
        State state = stateOrNull(ref);
        if (state == null) throw new PageUnavailableException();
        return state.revision;
    }

    static long revisionSnapshot(PageRef ref) { return ref == null || ref.stale ? -1 : ref.revision; }

    public static synchronized boolean canRun(ScriptContainer page, IScriptHandler handler) {
        try {
            Resolution resolution = resolve(page, handler);
            return available(resolution.effective);
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    static synchronized boolean enabled(PageRef ref) {
        State state = stateOrNull(ref);
        return state != null && observe(ref) && state.enabled && !state.conflicted && available(state.language);
    }

    static synchronized ScriptContainer page(PageRef ref) {
        State state = stateOrNull(ref);
        return state == null ? null : ref.page.get();
    }

    static synchronized IScriptHandler handler(PageRef ref) {
        State state = stateOrNull(ref);
        return state == null ? null : ref.handler.get();
    }

    static synchronized boolean serverOwned(PageRef ref) { return ref != null && ref.serverOwned; }

    public static synchronized void observeLoaded(ScriptContainer page, IScriptHandler handler) {
        if (page == null || handler == null || !handler.getScripts().contains(page)) return;
        try { describe(page, handler); }
        catch (RuntimeException | LinkageError unavailable) { /* canRun fails closed below */ }
    }

    /** Refreshes applied configuration and permanently invalidates removed/replaced pages. */
    static synchronized boolean observe(PageRef ref) {
        State state = stateOrNull(ref);
        if (state == null) return false;
        ScriptContainer page = ref.page.get();
        IScriptHandler handler = ref.handler.get();
        if (page == null || handler == null || !handler.getScripts().contains(page)) {
            STATES.remove(page);
            ref.stale = true;
            state.revision++;
            ref.revision = state.revision;
            ScriptLifecycleApi.invalidate(ref, DisposeReason.REMOVED);
            return false;
        }
        try {
            refresh(page, handler, state);
            return !state.conflicted;
        } catch (LanguageConflictException conflict) {
            return false;
        }
    }

    static synchronized String effectiveLanguage(ScriptContainer page, IScriptHandler handler) {
        if (page == null || handler == null || !handler.getScripts().contains(page)) return safe(handler == null ? null : handler.getLanguage());
        Resolution resolution = resolve(page, handler);
        return resolution.effective;
    }

    public static Carrier carrier(IScriptHandler handler) {
        if (handler instanceof PlayerScriptData) return Carrier.PLAYER;
        if (handler instanceof TileScriptedDoor) return Carrier.BLOCK_DOOR;
        if (handler instanceof TileScripted) return Carrier.BLOCK;
        if (handler instanceof ItemScriptedWrapper) return Carrier.ITEM;
        if (handler instanceof ForgeScriptData) return Carrier.FORGE;
        if (handler instanceof DataScript) return Carrier.NPC;
        return Carrier.UNKNOWN;
    }

    public static List<HookDescriptor> hooks(List<String> names, Carrier carrier) {
        if (names == null || names.isEmpty()) return List.of();
        Carrier actual = carrier == null ? Carrier.UNKNOWN : carrier;
        String context = switch (actual) {
            case NPC -> "NpcEvent";
            case PLAYER -> "PlayerEvent";
            case BLOCK, BLOCK_DOOR -> "BlockEvent";
            case ITEM -> "ItemEvent";
            case FORGE -> "ForgeEvent";
            case UNKNOWN -> "";
        };
        return names.stream().filter(java.util.Objects::nonNull).map(name -> {
            Class<?> type = context.isEmpty() ? null : top.cnpcplus.script.ScriptApiCompletion.eventType(context, name);
            return new HookDescriptor(name, actual,
                    type == null ? Optional.empty() : Optional.of(type.getCanonicalName() == null ? type.getName().replace('$', '.') : type.getCanonicalName()),
                    type == null ? Optional.empty() : Optional.of(type.getName()));
        }).toList();
    }

    private static State state(PageRef ref) {
        State state = stateOrNull(ref);
        if (state == null) throw new PageUnavailableException();
        return state;
    }

    private static State stateOrNull(PageRef ref) {
        if (ref == null) return null;
        if (ref.stale) return null;
        ScriptContainer page = ref.page.get();
        State state = page == null ? null : STATES.get(page);
        return state != null && state.ref == ref && ref.handler.get() == state.handler.get() ? state : null;
    }

    private static void requireLoaded(ScriptContainer page, IScriptHandler handler) {
        if (page == null || handler == null || !handler.getScripts().contains(page)) throw new PageUnavailableException();
    }

    private static void refresh(ScriptContainer page, IScriptHandler handler, State state) {
        Resolution resolution;
        try {
            resolution = resolve(page, handler);
        } catch (LanguageConflictException conflict) {
            if (!state.conflicted) {
                state.conflicted = true;
                state.revision++;
                state.ref.revision = state.revision;
                ScriptLifecycleApi.invalidate(state.ref, DisposeReason.LANGUAGE_CHANGED);
            }
            throw conflict;
        }
        String language = resolution.effective;
        String source = safe(page.script);
        List<String> linked = List.copyOf(page.scripts);
        Map<String, String> contents = linkedSources(page);
        boolean enabled = runtimeEnabled(handler, state.ref.serverOwned);
        boolean sourceChanged = !source.equals(state.source) || !linked.equals(state.linked)
                || !contents.equals(state.linkedContents);
        boolean languageChanged = !language.equals(state.language) || !resolution.override.equals(state.overrideLanguage)
                || state.conflicted;
        boolean enabledChanged = enabled != state.enabled;
        ScriptController controller = ScriptController.Instance;
        boolean reloaded = controller != null && (state.loadedGeneration != controller.lastLoaded
                || handler instanceof PlayerScriptData && state.playerUpdateGeneration != controller.lastPlayerUpdate);
        if (!sourceChanged && !languageChanged && !enabledChanged && !reloaded) return;
        state.source = source;
        state.linked = linked;
        state.linkedContents = contents;
        state.language = language;
        state.overrideLanguage = resolution.override;
        state.enabled = enabled;
        state.loadedGeneration = controller == null ? 0 : controller.lastLoaded;
        state.playerUpdateGeneration = controller == null || !(handler instanceof PlayerScriptData)
                ? 0 : controller.lastPlayerUpdate;
        state.conflicted = false;
        state.revision++;
        state.ref.revision = state.revision;
        if (sourceChanged) state.documentRevision++;
        ScriptPageApi.DisposeReason reason = reloaded ? DisposeReason.RELOADED : !enabled ? DisposeReason.DISABLED
                : enabledChanged ? DisposeReason.ENABLED
                : languageChanged ? DisposeReason.LANGUAGE_CHANGED : DisposeReason.SOURCE_CHANGED;
        ScriptLifecycleApi.invalidate(state.ref, reason);
    }

    private record Resolution(String effective, String override, boolean overridden) {}

    private static Resolution resolve(ScriptContainer page, IScriptHandler handler) {
        String defaultLanguage = safe(handler.getLanguage());
        Map<String, List<ResolverChoice>> candidates = new java.util.TreeMap<>();
        for (Resolver resolver : RESOLVERS) {
            Optional<String> value = resolver.provider.overrideLanguage(page, handler);
            if (value == null || value.isEmpty() || value.get().isBlank()) continue;
            String candidate = value.get();
            String normalized = candidate.strip().toLowerCase(java.util.Locale.ROOT);
            candidates.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(new ResolverChoice(resolver.id, candidate));
        }
        if (candidates.size() > 1) {
            List<String> ids = candidates.values().stream().flatMap(List::stream).map(ResolverChoice::id).distinct().sorted().toList();
            List<String> values = candidates.values().stream().map(choices -> choices.get(0).language).toList();
            throw new LanguageConflictException(ids.stream().distinct().sorted().toList(), List.copyOf(values));
        }
        if (candidates.isEmpty()) return new Resolution(defaultLanguage, "", false);
        String language = candidates.values().iterator().next().get(0).language;
        return new Resolution(language, language, true);
    }

    private record ResolverChoice(String id, String language) {}

    private static Map<String, String> linkedSources(ScriptContainer page) {
        ScriptController controller = ScriptController.Instance;
        if (controller == null || controller.scripts == null) return Map.of();
        var contents = new java.util.LinkedHashMap<String, String>();
        for (String name : page.scripts) {
            String text = controller.scripts.get(name);
            if (text != null) contents.putIfAbsent(name, text);
        }
        return java.util.Collections.unmodifiableMap(contents);
    }

    private static boolean available(String language) {
        ScriptController controller = ScriptController.Instance;
        return controller != null && controller.executorProviders.containsKey(language.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean runtimeEnabled(IScriptHandler handler, boolean serverOwned) {
        if (!handler.getEnabled()) return false;
        return !serverOwned || CustomNpcs.EnableScripting
                && (!(handler instanceof PlayerScriptData player) || player.isEnabled());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        synchronized (ScriptPageApi.class) {
            List<PageRef> pages = STATES.values().stream().filter(state -> state.ref.serverOwned)
                    .map(state -> state.ref).toList();
            for (PageRef page : pages) observe(page);
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        synchronized (ScriptPageApi.class) {
            for (State state : STATES.values()) {
                state.ref.stale = true;
                state.ref.revision = ++state.revision;
            }
            STATES.clear();
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
