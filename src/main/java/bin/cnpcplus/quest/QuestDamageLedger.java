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
    private static final class Death {
        final EntityLivingBase victim;
        final UUID leader;
        final java.util.Set<UUID> credited = new java.util.HashSet<>();
        Death(EntityLivingBase victim, UUID leader) { this.victim = victim; this.leader = leader; }
    }
    private final ThreadLocal<ArrayDeque<Death>> current = new ThreadLocal<ArrayDeque<Death>>() {
        @Override protected ArrayDeque<Death> initialValue() { return new ArrayDeque<Death>(); }
    };

    private QuestDamageLedger() {}

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
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
        shares.put(id, (old == null ? 0.0F : old) + Math.min(victim.getHealth(), event.getAmount()));
    }

    public void beginDeath(EntityLivingBase victim) {
        Map<UUID, Float> shares = damage.remove(victim);
        UUID leader = null;
        float largest = 0.0F;
        if (shares != null) for (Map.Entry<UUID, Float> entry : shares.entrySet()) {
            if (entry.getValue() > largest && victim.world.getMinecraftServer().getPlayerList().getPlayerByUUID(entry.getKey()) != null) {
                leader = entry.getKey(); largest = entry.getValue();
            }
        }
        current.get().push(new Death(victim, leader));
    }

    public void endDeath() {
        ArrayDeque<Death> stack = current.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) current.remove();
    }

    public EntityPlayer currentContributor() {
        Death death = current.get().peek();
        if (death == null || death.leader == null) return null;
        net.minecraft.server.MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        return server == null ? null : server.getPlayerList().getPlayerByUUID(death.leader);
    }

    public boolean creditedAny() {
        Death death = current.get().peek();
        return death != null && !death.credited.isEmpty();
    }

    public boolean creditOnce(EntityPlayer player, EntityLivingBase victim) {
        Death death = current.get().peek();
        return death == null || death.victim != victim || death.credited.add(player.getUniqueID());
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
