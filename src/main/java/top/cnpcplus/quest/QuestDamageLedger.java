package top.cnpcplus.quest;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import noppes.npcs.entity.EntityNPCInterface;

/** Actual applied damage shares, used only for the death currently being processed. */
public final class QuestDamageLedger {
    private static final class Damage { final Map<UUID, Double> players = new HashMap<>(); }
    private static final class Death { final UUID credited; Death(UUID credited) { this.credited = credited; } }
    private static final Map<LivingEntity, Damage> DAMAGE = new WeakHashMap<>();
    private static final ThreadLocal<Death> CURRENT_DEATH = new ThreadLocal<>();

    private QuestDamageLedger() {}

    @SubscribeEvent
    public static void record(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.getAmount() <= 0) return;
        Damage damage = DAMAGE.computeIfAbsent(victim, ignored -> new Damage());
        double amount = event.getAmount();
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

    public static void beginDeath(LivingEntity victim) {
        Player player = evaluate(victim);
        CURRENT_DEATH.set(new Death(player == null ? null : player.getUUID()));
    }
    public static Player currentCreditedPlayer() {
        Death death = CURRENT_DEATH.get();
        if (death == null || death.credited == null) return null;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(death.credited);
    }
    public static void endDeath() { CURRENT_DEATH.remove(); }

    private static Player responsiblePlayer(Entity source) {
        if (source instanceof Player player) return player;
        if (source instanceof Projectile projectile && projectile.getOwner() instanceof Player player) return player;
        if (source instanceof EntityNPCInterface npc && npc.getOwner() instanceof Player player) return player;
        if (source instanceof TamableAnimal animal && animal.getOwner() instanceof Player player) return player;
        return null;
    }
}
