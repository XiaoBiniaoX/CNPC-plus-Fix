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

    static BooleanSupplier current() {
        Context context = CURRENT.get();
        if (context == null) return INHERITED.get() == null ? () -> true : INHERITED.get();
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
        return () -> {
            var active = scriptRef.get();
            var handler = handlerRef.get();
            return CustomNpcs.EnableScripting && controller == ScriptController.Instance && controller.lastLoaded == loaded
                    && active != null && !active.errored && handler != null
                    && (handler instanceof PlayerScriptData player ? player.isEnabled() && controller.lastPlayerUpdate == playerUpdate : handler.getEnabled())
                    && handler.getScripts().contains(active) && Objects.equals(code, active.script)
                    && links.equals(active.scripts) && Objects.equals(language, handler.getLanguage()) && alive.getAsBoolean();
        };
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
