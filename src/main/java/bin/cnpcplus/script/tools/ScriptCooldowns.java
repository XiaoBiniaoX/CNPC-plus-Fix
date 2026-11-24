package bin.cnpcplus.script.tools;

import java.util.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.item.IItemScripted;

/** Server ticks; entities use UUID, tiles/items use runtime identity without retaining unloaded owners. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptCooldowns {
    private static final Map<UUID, Map<String, Long>> ENTITIES = new HashMap<>();
    private static final Map<Object, Map<String, Long>> OBJECTS = new WeakHashMap<>();
    private static long clock;
    public void start(Object owner, String id, int durationTicks) {
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must be nonnegative");
        Map<String, Long> ends = timers(owner, id);
        if (durationTicks == 0) ends.remove(id); else ends.put(id, clock + durationTicks);
    }
    public int getRemaining(Object owner, String id) { return (int)Math.max(0, timers(owner, id).getOrDefault(id, clock) - clock); }
    public boolean isReady(Object owner, String id) { return getRemaining(owner, id) == 0; }
    public boolean tryStart(Object owner, String id, int durationTicks) {
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must be nonnegative");
        Map<String, Long> ends = timers(owner, id);
        if (ends.getOrDefault(id, clock) > clock) return false;
        if (durationTicks > 0) ends.put(id, clock + durationTicks); return true;
    }
    public void clear(Object owner, String id) { timers(owner, id).remove(id); }
    private static Map<String, Long> timers(Object owner, String id) {
        ScriptWorld.server();
        if (id == null || id.trim().isEmpty() || id.length() > 128) throw new IllegalArgumentException("Invalid cooldown ID");
        if (owner instanceof IEntity<?>) return ENTITIES.computeIfAbsent(((IEntity<?>)owner).getMCEntity().getUniqueID(), ignored -> new HashMap<>());
        Object key = owner instanceof IBlock ? ((IBlock)owner).getMCTileEntity() : owner instanceof IItemScripted ? ((IItemScripted)owner).getMCItemStack() : null;
        if (key == null) throw new IllegalArgumentException("Expected NPC, player, scripted block, door or carrier");
        return OBJECTS.computeIfAbsent(key, ignored -> new HashMap<>());
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        clock++;
        if (clock % 20 != 0) return;
        for (Map<?, Map<String, Long>> owners : Arrays.asList(ENTITIES, OBJECTS)) owners.values().removeIf(ends -> {
            ends.values().removeIf(end -> end <= clock); return ends.isEmpty();
        });
    }
    public static void stop() { ENTITIES.clear(); OBJECTS.clear(); clock = 0; }
}
