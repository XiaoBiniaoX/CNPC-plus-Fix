package top.cnpcplus.script.tools;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import noppes.npcs.CustomNpcs;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.event.BlockEvent;
import noppes.npcs.api.event.ItemEvent;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.event.PlayerEvent;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.ScriptController;
import noppes.npcs.controllers.data.PlayerScriptData;

public final class ScriptScope {
    private record Context(ScriptContainer script, IScriptHandler handler, Object event) {}
    private record Lifetime(WeakReference<ScriptContainer> script, WeakReference<IScriptHandler> handler,
                            BooleanSupplier alive) implements BooleanSupplier {
        @Override public boolean getAsBoolean() { return alive.getAsBoolean(); }
    }
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<BooleanSupplier> INHERITED = new ThreadLocal<>();

    private ScriptScope() {}

    public static String run(ScriptContainer script, IScriptHandler handler, Object event, Supplier<String> action) {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(script, handler, event));
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static void callback(BooleanSupplier owner, Runnable action) {
        BooleanSupplier previous = INHERITED.get();
        Context context = CURRENT.get();
        CURRENT.remove();
        INHERITED.set(owner);
        try {
            action.run();
        } finally {
            if (previous == null) INHERITED.remove();
            else INHERITED.set(previous);
            if (context != null) CURRENT.set(context);
        }
    }

    static ScriptContainer script() {
        Context context = CURRENT.get();
        ScriptContainer script = context != null ? context.script
                : INHERITED.get() instanceof Lifetime lifetime ? lifetime.script.get() : null;
        if (script == null) throw new IllegalStateException("CNPCPLUS.EVENTS.SCRIPT_CONTEXT_REQUIRED");
        return script;
    }

    static BooleanSupplier withLifetime(BooleanSupplier scope, BooleanSupplier condition) {
        BooleanSupplier alive = () -> scope.getAsBoolean() && condition.getAsBoolean();
        return scope instanceof Lifetime lifetime ? new Lifetime(lifetime.script, lifetime.handler, alive) : alive;
    }

    /** Public bridge for the lifecycle facade; the ThreadLocal and its concrete lifetime remain private. */
    public static ScriptContainer currentScript() {
        Context context = CURRENT.get();
        if (context != null) return context.script;
        return INHERITED.get() instanceof Lifetime lifetime ? lifetime.script.get() : null;
    }

    /** Public bridge for the lifecycle facade; returns null for an unrelated callback. */
    public static IScriptHandler currentHandler() {
        Context context = CURRENT.get();
        if (context != null) return context.handler;
        return INHERITED.get() instanceof Lifetime lifetime ? lifetime.handler.get() : null;
    }

    /** Restore page ownership for a server-thread callback without restoring the old event object. */
    public static void runWith(ScriptContainer script, IScriptHandler handler, Runnable action) {
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(handler, "handler");
        Objects.requireNonNull(action, "action");
        Context previous = CURRENT.get();
        CURRENT.set(new Context(script, handler, null));
        try {
            action.run();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    /** Run a cleanup callback without inheriting the invalidated page or event context. */
    public static void runUnscoped(Runnable action) {
        Context previous = CURRENT.get();
        BooleanSupplier inherited = INHERITED.get();
        CURRENT.remove();
        INHERITED.remove();
        try {
            action.run();
        } finally {
            if (inherited != null) INHERITED.set(inherited);
            if (previous != null) CURRENT.set(previous);
        }
    }

    static BooleanSupplier current() {
        Context context = CURRENT.get();
        if (context == null) return INHERITED.get() == null ? () -> true : INHERITED.get();
        var pageLifetime = top.cnpcplus.api.script.ScriptLifecycleApi.current().orElse(null);
        ScriptContainer script = context.script;
        var scriptRef = new WeakReference<>(script);
        var handlerRef = new WeakReference<>(context.handler);
        Object event = context.event;
        BooleanSupplier alive;
        if (event instanceof NpcEvent npc) alive = owner(npc.npc);
        else if (event instanceof PlayerEvent player) alive = owner(player.player);
        else if (event instanceof BlockEvent block) alive = owner(block.block);
        else if (event instanceof ItemEvent item) {
            var weak = new WeakReference<>(item.item.getMCItemStack());
            alive = () -> { var stack = weak.get(); return stack != null && !stack.isEmpty(); };
        } else alive = () -> true;
        var controller = ScriptController.Instance;
        long loaded = controller.lastLoaded, playerUpdate = controller.lastPlayerUpdate;
        String code = script.script, language = context.handler.getLanguage();
        List<String> links = List.copyOf(script.scripts);
        return new Lifetime(scriptRef, handlerRef, () -> {
            var active = scriptRef.get();
            var handler = handlerRef.get();
            return pageLifetime != null && pageLifetime.isValid()
                    && CustomNpcs.EnableScripting && controller == ScriptController.Instance && controller.lastLoaded == loaded
                    && active != null && handler != null
                    && (handler instanceof PlayerScriptData player ? player.isEnabled() && controller.lastPlayerUpdate == playerUpdate : handler.getEnabled())
                    && handler.getScripts().contains(active) && Objects.equals(code, active.script)
                    && links.equals(active.scripts) && Objects.equals(language, handler.getLanguage()) && alive.getAsBoolean();
        });
    }

    static BooleanSupplier owner(Object owner) {
        if (owner instanceof IWorld world) {
            ServerLevel level = ScriptWorld.level(world);
            var dimension = level.dimension();
            var weak = new WeakReference<>(level);
            return () -> { var current = weak.get(); return current != null && ScriptWorld.server().getLevel(dimension) == current; };
        }
        if (owner instanceof IEntity<?> wrapper) {
            var entity = wrapper.getMCEntity();
            var uuid = entity.getUUID();
            if (entity instanceof ServerPlayer) return () -> ScriptWorld.server().getPlayerList().getPlayer(uuid) != null;
            var dimension = entity.level().dimension();
            return () -> { var level = ScriptWorld.server().getLevel(dimension); return level != null && level.getEntity(uuid) != null; };
        }
        if (owner instanceof IBlock block) {
            BlockEntity tile = block.getMCTileEntity();
            if (tile != null) {
                var weak = new WeakReference<>(tile);
                return () -> { var current = weak.get(); return current != null && !current.isRemoved(); };
            }
        }
        throw new IllegalArgumentException("Expected world, entity, scripted block or door");
    }
}
