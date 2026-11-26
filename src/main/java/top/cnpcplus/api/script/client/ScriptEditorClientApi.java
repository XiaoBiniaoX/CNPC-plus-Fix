package top.cnpcplus.api.script.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import top.cnpcplus.api.script.ScriptPageApi;

/** Client-only action slots for the native script editor. */
@OnlyIn(Dist.CLIENT)
public final class ScriptEditorClientApi {
    public enum Placement { LANGUAGE_ACTION, SIDEBAR_ACTION }

    public record Area(int x, int y, int width, int height) {}

    public record EditorContext(ScriptPageApi.Carrier carrier, String defaultLanguage,
                                String overrideLanguage, String effectiveLanguage,
                                List<ScriptPageApi.HookDescriptor> hooks) {
        public EditorContext {
            carrier = carrier == null ? ScriptPageApi.Carrier.UNKNOWN : carrier;
            defaultLanguage = defaultLanguage == null ? "" : defaultLanguage;
            overrideLanguage = overrideLanguage == null ? "" : overrideLanguage;
            effectiveLanguage = effectiveLanguage == null ? "" : effectiveLanguage;
            hooks = List.copyOf(hooks);
        }
    }

    public interface EditorSession {
        EditorContext context();
        ScriptPageApi.SourceBundle snapshot();
        void flushDraft();
        boolean requestSave();
        void refresh();
        void openSubGui(GuiBasic screen);
    }

    public record Action(String label, Consumer<EditorSession> onPress) {
        public Action {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(onPress, "onPress");
        }
    }

    @FunctionalInterface
    public interface Visibility { boolean visible(EditorContext context); }

    @FunctionalInterface
    public interface ActionFactory { List<Action> create(EditorSession session, Area available); }

    private record Registration(Placement placement, Visibility visibility, ActionFactory factory) {}
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+");
    private static volatile Map<String, Registration> ACTIONS = Map.of();
    private static final Map<String, String> FAILURES = new ConcurrentHashMap<>();

    private ScriptEditorClientApi() {}

    /** Register a fixed-slot action; re-registering the same ID replaces it without duplicating widgets. */
    public static synchronized void registerAction(String id, Placement placement,
                                                   Visibility visibility, ActionFactory factory) {
        if (id == null || !ID.matcher(id).matches()) throw new IllegalArgumentException("id");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(factory, "factory");
        var updated = new LinkedHashMap<>(ACTIONS);
        updated.put(id, new Registration(placement, visibility, factory));
        ACTIONS = Map.copyOf(updated);
        FAILURES.remove(id);
    }

    public static synchronized void unregisterAction(String id) {
        if (id == null || !ACTIONS.containsKey(id)) return;
        var updated = new LinkedHashMap<>(ACTIONS);
        updated.remove(id);
        ACTIONS = Map.copyOf(updated);
        FAILURES.remove(id);
    }

    /** Bounded provider diagnostic; contains no editor text or exception message. */
    public static String lastFailure(String id) { return FAILURES.getOrDefault(id, ""); }

    public static List<Action> actions(Placement placement, EditorSession session, Area available) {
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(available, "available");
        EditorContext context = session.context();
        var result = new ArrayList<Action>();
        ACTIONS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            Registration registration = entry.getValue();
            if (registration.placement != placement) return;
            try {
                if (!registration.visibility.visible(context)) return;
                List<Action> actions = Objects.requireNonNull(registration.factory.create(session, available), "actions");
                actions.forEach(action -> result.add(Objects.requireNonNull(action, "action")));
                FAILURES.remove(entry.getKey());
            } catch (RuntimeException | LinkageError failure) {
                if (FAILURES.size() >= 128 && !FAILURES.containsKey(entry.getKey()))
                    FAILURES.keySet().stream().findFirst().ifPresent(FAILURES::remove);
                FAILURES.put(entry.getKey(), failure.getClass().getSimpleName());
            }
        });
        return List.copyOf(result);
    }
}
