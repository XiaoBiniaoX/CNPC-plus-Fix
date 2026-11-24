package bin.cnpcplus.script.tools;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.event.*;
import noppes.npcs.controllers.*;
import noppes.npcs.controllers.data.PlayerScriptData;

/** Weak owner checks; queued closures are released when their script or owner expires. */
public final class ScriptScope {
    private static final class Lifetime implements BooleanSupplier {
        final WeakReference<ScriptContainer> script;
        final WeakReference<IScriptHandler> handler;
        final BooleanSupplier alive;
        final ScriptContainer previous;
        Lifetime(ScriptContainer script, IScriptHandler handler, BooleanSupplier alive) { this(script, handler, alive, null); }
        Lifetime(ScriptContainer script, IScriptHandler handler, BooleanSupplier alive, ScriptContainer previous) {
            this.script = new WeakReference<>(script); this.handler = new WeakReference<>(handler); this.alive = alive;
            this.previous = previous;
        }
        public boolean getAsBoolean() { return alive.getAsBoolean(); }
    }
    private static final ThreadLocal<Deque<Lifetime>> RUNNING = ThreadLocal.withInitial(ArrayDeque::new);
    private ScriptScope() { }
    public static void observe(ScriptContainer script, IScriptHandler handler, Object event) {
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            RUNNING.get().push(new Lifetime(script, handler, () -> false, ScriptContainer.Current));
            return;
        }
        BooleanSupplier alive;
        if (event instanceof NpcEvent) alive = owner(((NpcEvent)event).npc);
        else if (event instanceof PlayerEvent) alive = owner(((PlayerEvent)event).player);
        else if (event instanceof BlockEvent) alive = owner(((BlockEvent)event).block);
        else if (event instanceof ItemEvent) {
            WeakReference<net.minecraft.item.ItemStack> weak = new WeakReference<>(((ItemEvent)event).item.getMCItemStack());
            alive = () -> {
                net.minecraft.item.ItemStack stack = weak.get();
                if (stack == null || stack.isEmpty()) return false;
                for (EntityPlayerMP player : ScriptWorld.server().getPlayerList().getPlayers())
                    for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++)
                        if (player.inventory.getStackInSlot(slot) == stack) return true;
                return false;
            };
        } else alive = () -> true;
        WeakReference<ScriptContainer> scriptRef = new WeakReference<>(script);
        WeakReference<IScriptHandler> handlerRef = new WeakReference<>(handler);
        ScriptController controller = ScriptController.Instance;
        long loaded = controller.lastLoaded, playerUpdate = controller.lastPlayerUpdate;
        String code = script.script, language = handler.getLanguage(); List<String> links = new ArrayList<>(script.scripts);
        RUNNING.get().push(new Lifetime(script, handler, () -> {
            ScriptContainer active = scriptRef.get(); IScriptHandler current = handlerRef.get();
            return CustomNpcs.EnableScripting && controller == ScriptController.Instance && controller.lastLoaded == loaded
                    && active != null && !active.errored && current != null && current.getEnabled()
                    && (!(current instanceof PlayerScriptData) || controller.lastPlayerUpdate == playerUpdate)
                    && current.getScripts().contains(active) && Objects.equals(code, active.script)
                    && links.equals(active.scripts) && Objects.equals(language, current.getLanguage()) && alive.getAsBoolean();
        }, ScriptContainer.Current));
    }
    public static void leave() {
        Deque<Lifetime> stack = RUNNING.get();
        if (!stack.isEmpty()) ScriptContainer.Current = stack.pop().previous;
        if (stack.isEmpty()) RUNNING.remove();
    }
    static BooleanSupplier current() {
        Lifetime scope = RUNNING.get().peek();
        return scope == null ? () -> true : scope;
    }
    static ScriptContainer script() {
        Lifetime scope = RUNNING.get().peek();
        ScriptContainer script = scope == null ? null : scope.script.get();
        if (script == null) throw new IllegalStateException("CNPCPLUS.EVENTS.SCRIPT_CONTEXT_REQUIRED");
        return script;
    }
    static IScriptHandler handler() { return RUNNING.get().element().handler.get(); }
    static BooleanSupplier withLifetime(BooleanSupplier scope, BooleanSupplier condition) {
        BooleanSupplier alive = () -> scope.getAsBoolean() && condition.getAsBoolean();
        if (!(scope instanceof Lifetime)) return alive;
        Lifetime source = (Lifetime)scope;
        return new Lifetime(source.script.get(), source.handler.get(), alive);
    }
    static void callback(BooleanSupplier owner, Runnable action) {
        ScriptContainer previous = ScriptContainer.Current;
        Lifetime source = owner instanceof Lifetime ? (Lifetime)owner : null;
        Lifetime scope = source == null ? new Lifetime(null, null, owner, previous)
                : new Lifetime(source.script.get(), source.handler.get(), owner, previous);
        RUNNING.get().push(scope); ScriptContainer.Current = scope.script.get();
        try { synchronized ("lock") { action.run(); } } finally { leave(); }
    }
    static BooleanSupplier owner(Object owner) {
        if (owner instanceof IWorld) {
            WorldServer level = ((IWorld)owner).getMCWorld(); int dimension = level.provider.getDimension(); WeakReference<WorldServer> weak = new WeakReference<>(level);
            return () -> weak.get() != null && ScriptWorld.server().getWorld(dimension) == weak.get();
        }
        if (owner instanceof IEntity<?>) {
            Entity entity = ((IEntity<?>)owner).getMCEntity(); UUID id = entity.getUniqueID();
            if (entity instanceof EntityPlayerMP) return () -> ScriptWorld.server().getPlayerList().getPlayerByUUID(id) != null;
            int dimension = entity.dimension;
            return () -> { WorldServer world = ScriptWorld.server().getWorld(dimension); Entity live = world == null ? null : world.getEntityFromUuid(id); return live != null && !live.isDead; };
        }
        if (owner instanceof IBlock) {
            TileEntity tile = ((IBlock)owner).getMCTileEntity();
            if (tile != null) {
                WeakReference<TileEntity> weak = new WeakReference<>(tile);
                return () -> {
                    TileEntity current = weak.get();
                    return current != null && !current.isInvalid() && current.getWorld() instanceof WorldServer
                            && ScriptWorld.server().getWorld(current.getWorld().provider.getDimension()) == current.getWorld()
                            && ScriptWorld.loaded((WorldServer)current.getWorld(), current.getPos());
                };
            }
        }
        throw new IllegalArgumentException("Expected world, entity, scripted block or door");
    }
    public static void stop() { RUNNING.remove(); }
}
