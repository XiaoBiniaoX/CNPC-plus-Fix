package top.cnpcplus.script.tools;

import java.util.HashMap;
import java.util.Map;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.api.block.IBlock;
import noppes.npcs.api.entity.IEntity;
import noppes.npcs.api.item.IItemScripted;

/** Runtime-only cooldowns, measured against a single server tick clock across dimensions. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptCooldowns {
    private record Key(Object owner, String id) {}
    private record EntityOwner(java.util.UUID uuid) {}
    private static final Map<Key, Integer> ENDS = new HashMap<>();

    public void start(Object owner, String id, int durationTicks) {
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must be nonnegative");
        int tick = ScriptWorld.server().getTickCount();
        Key key = key(owner, id);
        if (durationTicks == 0) ENDS.remove(key);
        else ENDS.put(key, tick + durationTicks);
    }

    public int getRemaining(Object owner, String id) {
        int tick = ScriptWorld.server().getTickCount();
        return Math.max(0, ENDS.getOrDefault(key(owner, id), tick) - tick);
    }

    public boolean isReady(Object owner, String id) { return getRemaining(owner, id) == 0; }

    public boolean tryStart(Object owner, String id, int durationTicks) {
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must be nonnegative");
        int tick = ScriptWorld.server().getTickCount();
        Key key = key(owner, id);
        if (ENDS.getOrDefault(key, tick) - tick > 0) return false;
        if (durationTicks > 0) ENDS.put(key, tick + durationTicks);
        return true;
    }

    public void clear(Object owner, String id) {
        ScriptWorld.server();
        ENDS.remove(key(owner, id));
    }

    private static Key key(Object owner, String id) {
        if (id == null || id.isBlank() || id.length() > 128) throw new IllegalArgumentException("Invalid cooldown ID");
        if (owner instanceof IEntity<?> entity) return new Key(new EntityOwner(entity.getMCEntity().getUUID()), id);
        if (owner instanceof IBlock block && block.getMCTileEntity() != null) return new Key(block.getMCTileEntity(), id);
        if (owner instanceof IItemScripted item) return new Key(item.getMCItemStack(), id);
        throw new IllegalArgumentException("Expected NPC, player, scripted block, door or carrier");
    }

    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        int now = event.getServer().getTickCount();
        if (now % 20 == 0) ENDS.values().removeIf(end -> end - now <= 0);
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) { ENDS.clear(); }
}
