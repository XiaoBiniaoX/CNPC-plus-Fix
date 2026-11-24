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
    private static final Map<ScriptContainer, BooleanSupplier> CONTEXT = new WeakHashMap<>();
    private static final ThreadLocal<BooleanSupplier> INHERITED = new ThreadLocal<>();
    private ScriptScope() { }
    public static void observe(ScriptContainer script, IScriptHandler handler, Object event) {
        if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getEffectiveSide().isClient()) return;
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
        CONTEXT.put(script, () -> {
            ScriptContainer active = scriptRef.get(); IScriptHandler current = handlerRef.get();
            return CustomNpcs.EnableScripting && controller == ScriptController.Instance && controller.lastLoaded == loaded
                    && active != null && !active.errored && current != null && current.getEnabled()
                    && (!(current instanceof PlayerScriptData) || controller.lastPlayerUpdate == playerUpdate)
                    && current.getScripts().contains(active) && Objects.equals(code, active.script)
                    && links.equals(active.scripts) && Objects.equals(language, current.getLanguage()) && alive.getAsBoolean();
        });
    }
    static BooleanSupplier current() {
        BooleanSupplier scope = ScriptContainer.Current == null ? null : CONTEXT.get(ScriptContainer.Current);
        return scope != null ? scope : INHERITED.get() != null ? INHERITED.get() : () -> true;
    }
    static void callback(BooleanSupplier owner, Runnable action) {
        BooleanSupplier previous = INHERITED.get(); INHERITED.set(owner);
        try { action.run(); } finally { if (previous == null) INHERITED.remove(); else INHERITED.set(previous); }
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
    public static void stop() { CONTEXT.clear(); INHERITED.remove(); }
}
