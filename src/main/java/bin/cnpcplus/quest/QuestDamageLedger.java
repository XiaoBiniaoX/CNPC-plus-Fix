package bin.cnpcplus.quest;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.entity.EntityNPCInterface;

/** Tracks applied damage and exposes the leading online contributor during native death handling. */
public final class QuestDamageLedger {
    public static final QuestDamageLedger INSTANCE = new QuestDamageLedger();
    private final Map<EntityLivingBase, Map<UUID, Float>> damage = new WeakHashMap<EntityLivingBase, Map<UUID, Float>>();
    private final ThreadLocal<ArrayDeque<UUID>> current = new ThreadLocal<ArrayDeque<UUID>>() {
        @Override protected ArrayDeque<UUID> initialValue() { return new ArrayDeque<UUID>(); }
    };

    private QuestDamageLedger() {}

    @SubscribeEvent
    public void record(LivingDamageEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote || event.getAmount() <= 0.0F) return;
        Entity responsible = NoppesUtilServer.GetDamageSourcee(event.getSource());
        EntityPlayer player = responsiblePlayer(responsible);
        if (player == null) return;
        Map<UUID, Float> shares = damage.get(victim);
        if (shares == null) {
            shares = new LinkedHashMap<UUID, Float>();
            damage.put(victim, shares);
        }
        UUID id = player.getUniqueID();
        Float old = shares.get(id);
        shares.put(id, (old == null ? 0.0F : old) + event.getAmount());
    }

    public void beginDeath(EntityLivingBase victim) {
        Map<UUID, Float> shares = damage.remove(victim);
        UUID leader = null;
        float largest = 0.0F;
        if (shares != null) for (Map.Entry<UUID, Float> entry : shares.entrySet()) {
            if (entry.getValue() > largest) { leader = entry.getKey(); largest = entry.getValue(); }
        }
        current.get().push(leader == null ? new UUID(0L, 0L) : leader);
    }

    public void endDeath() {
        ArrayDeque<UUID> stack = current.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) current.remove();
    }

    public EntityPlayer currentContributor() {
        UUID id = current.get().peek();
        if (id == null || (id.getMostSignificantBits() == 0L && id.getLeastSignificantBits() == 0L)) return null;
        net.minecraft.server.MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(id);
    }

    private static EntityPlayer responsiblePlayer(Entity entity) {
        if (entity instanceof EntityPlayer) return (EntityPlayer)entity;
        if (entity instanceof EntityNPCInterface && ((EntityNPCInterface)entity).getOwner() instanceof EntityPlayer)
            return (EntityPlayer)((EntityNPCInterface)entity).getOwner();
        if (entity instanceof EntityTameable && ((EntityTameable)entity).getOwner() instanceof EntityPlayer)
            return (EntityPlayer)((EntityTameable)entity).getOwner();
        return null;
    }
}
