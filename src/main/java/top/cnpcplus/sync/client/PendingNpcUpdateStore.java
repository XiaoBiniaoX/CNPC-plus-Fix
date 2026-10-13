package top.cnpcplus.sync.client;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * S1：PacketNpcUpdate 在实体尚未注册时缓存，等实体出现后补放。
 * 实体加载区块晚于同步包到达是「同步了也不一定有用」的总放大器。
 * 仅客户端加载（专用服务器不会触碰本类）。
 */
public final class PendingNpcUpdateStore {
    private static final Map<Integer, Entry> PENDING = new ConcurrentHashMap<>();
    /** 等待上限 ~10 秒（200 tick）。 */
    private static final long MAX_AGE_MS = 10_000L;

    private PendingNpcUpdateStore() {}

    public static void store(int id, CompoundTag data) {
        if (id < 0 || data == null) return;
        PENDING.put(id, new Entry(data.copy(), System.currentTimeMillis()));
    }

    /** 客户端每 tick 调用：实体出现则补放，超时丢弃。 */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            PENDING.clear();
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Integer, Entry>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Entry> e = it.next();
            Entity entity = mc.level.getEntity(e.getKey());
            if (entity instanceof EntityNPCInterface npc) {
                try {
                    npc.readSpawnData(e.getValue().data);
                } catch (Throwable ignored) {
                }
                it.remove();
            } else if (now - e.getValue().createdMs > MAX_AGE_MS) {
                it.remove();
            }
        }
    }

    private static final class Entry {
        final CompoundTag data;
        final long createdMs;

        Entry(CompoundTag data, long createdMs) {
            this.data = data;
            this.createdMs = createdMs;
        }
    }
}
