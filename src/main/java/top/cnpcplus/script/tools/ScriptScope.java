package top.cnpcplus.script.tools;

import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.event.BlockEvent;
import noppes.npcs.api.event.ItemEvent;
import noppes.npcs.api.event.NpcEvent;
import noppes.npcs.api.event.PlayerEvent;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import bin.cnpcplus.script.ScriptPageState;
import java.util.Optional;
import top.cnpcplus.api.script.ScriptMetadataApi;
import top.cnpcplus.api.script.ScriptScopeApi.ScriptExecutionContext;
import top.cnpcplus.api.script.ScriptScopeApi.ScriptLifetime;

public final class ScriptScope {
    private record Context(ScriptContainer script, IScriptHandler handler, String hook, Object event) {}
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<BooleanSupplier> INHERITED = new ThreadLocal<>();

    private ScriptScope() {}

    public static String run(ScriptContainer script, IScriptHandler handler, Object event, Supplier<String> action) {
        return run(script, handler, "", event, action);
    }

    public static String run(ScriptContainer script, IScriptHandler handler, String hook, Object event, Supplier<String> action) {
        ScriptPageState.of(script).begin(eventOwner(event));
        Context previous = CURRENT.get();
        CURRENT.set(new Context(script, handler, hook, event));
        try {
            return action.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static void callback(BooleanSupplier owner, Runnable action) {
        BooleanSupplier previous = INHERITED.get();
        Context previousContext = CURRENT.get();
        CURRENT.remove();
        INHERITED.set(owner);
        try {
            action.run();
        } finally {
            if (previous == null) INHERITED.remove();
            else INHERITED.set(previous);
            if (previousContext != null) CURRENT.set(previousContext);
        }
    }

    public static Optional<ScriptExecutionContext> context() {
        Context context = CURRENT.get();
        return context == null ? Optional.empty() : Optional.of(new ScriptExecutionContext(context.script, context.handler,
                context.hook, ScriptPageState.of(context.script).effectiveLanguage(), ScriptMetadataApi.contextId(context.handler)));
    }

    public static Optional<ScriptLifetime> capture() {
        Context context = CURRENT.get();
        if (context != null) return Optional.of(ScriptPageState.of(context.script).lifetime());
        BooleanSupplier inherited = INHERITED.get();
        return inherited == null ? Optional.empty() : Optional.of(inherited::getAsBoolean);
    }

    static BooleanSupplier current() {
        var lifetime = capture();
        return lifetime.isPresent() ? lifetime.get()::isValid : () -> true;
    }

    private static BooleanSupplier eventOwner(Object event) {
        if (event instanceof NpcEvent npc) return owner(npc.npc);
        else if (event instanceof PlayerEvent player) return owner(player.player);
        else if (event instanceof BlockEvent block) return owner(block.block);
        else if (event instanceof ItemEvent item) {
            var weak = new WeakReference<>(item.item.getMCItemStack());
            return () -> { var stack = weak.get(); return stack != null && !stack.isEmpty(); };
        } else return () -> true;
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
