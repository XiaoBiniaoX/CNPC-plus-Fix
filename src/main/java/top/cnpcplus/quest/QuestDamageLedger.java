package top.cnpcplus.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

/** Actual applied damage shares, used only for the death currently being processed. */
public final class QuestDamageLedger {
    private static final class Damage { final Map<UUID, Double> players = new HashMap<>(); }
    private static final Map<LivingEntity, Damage> DAMAGE = new WeakHashMap<>();

    private QuestDamageLedger() {}

    @SubscribeEvent
    public static void record(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0) return;
        Damage damage = DAMAGE.computeIfAbsent(victim, ignored -> new Damage());
        double amount = event.getNewDamage();
        var source = event.getSource();
        Player player = responsiblePlayer(source.getEntity());
        if (player == null) player = responsiblePlayer(source.getDirectEntity());
        if (player != null) damage.players.merge(player.getUUID(), amount, Double::sum);
    }

    public static Player evaluate(LivingEntity victim) {
        Damage damage = DAMAGE.remove(victim);
        UUID credited = damage == null ? null : selectContributor(damage.players);
        if (credited == null || victim.level().getServer() == null) return null;
        return victim.level().getServer().getPlayerList().getPlayer(credited);
    }

    static UUID selectContributor(Map<UUID, Double> contributions) {
        UUID credited = null;
        double largest = 0;
        for (Map.Entry<UUID, Double> entry : contributions.entrySet()) {
            if (entry.getValue() > largest) { largest = entry.getValue(); credited = entry.getKey(); }
        }
        return credited;
    }

    private static Player responsiblePlayer(Entity source) {
        if (source instanceof Player player) return player;
        if (source instanceof Projectile projectile && projectile.getOwner() instanceof Player player) return player;
        if (source instanceof EntityNPCInterface npc && npc.getOwner() instanceof Player player) return player;
        if (source instanceof TamableAnimal animal && animal.getOwner() instanceof Player player) return player;
        return null;
    }
}
