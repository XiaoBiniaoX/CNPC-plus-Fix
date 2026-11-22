package top.cnpcplus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.cnpcplus.script.ScriptItemTimers;
import top.cnpcplus.transport.TransportFare;
import top.cnpcplus.ranged.ProjectileExplosionBuffs;
import top.cnpcplus.model.PartTransform;
import noppes.npcs.entity.EntityNPCInterface;

public final class Phase3Regression {
    public static void main(String[] args) {
        timers();
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException) || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
        }
        fares();
        partTransforms();
        projectileDamage();
        System.out.println("PHASE3_FARE_AND_TIMERS=PASS");
    }

    private static void timers() {
        var timers = new ScriptItemTimers();
        var fired = new ArrayList<Integer>();
        timers.start(1, 2, true);
        timers.tick(fired::add);
        check(fired.isEmpty(), "timer fired before its interval");
        timers.tick(id -> {
            fired.add(id);
            timers.forceStart(1, 3, false);
            timers.start(2, 1, false);
        });
        check(fired.equals(List.of(1)), "callback-started timer ran in the same update");
        timers.tick(fired::add);
        timers.tick(fired::add);
        check(fired.equals(List.of(1, 2)) && !timers.has(2), "one-shot timer was not removed");
        timers.tick(fired::add);
        check(fired.equals(List.of(1, 2, 1)) && !timers.has(1), "replacement did not start a new interval");
    }

    private static void fares() {
        var inventory = new Inventory(null);
        var costs = new ArrayList<>(Collections.nCopies(TransportFare.SLOTS, ItemStack.EMPTY));
        costs.set(0, new ItemStack(Items.EMERALD, 3));
        costs.set(8, new ItemStack(Items.EMERALD, 4));
        var saved = TransportFare.save(true, costs);
        var restored = TransportFare.items(saved);
        check(TransportFare.save(true, restored).equals(saved), "fare entries did not round-trip");
        inventory.items.set(0, new ItemStack(Items.EMERALD, 6));
        inventory.items.set(1, new ItemStack(Items.EMERALD, 2).setHoverName(Component.literal("Other")));
        var requirements = TransportFare.requirements(inventory, restored);
        check(requirements.size() == 1 && requirements.get(0).needed() == 7 && requirements.get(0).owned() == 6
                && !requirements.get(0).affordable(), "fare display counts must aggregate matching stacks and ignore differing NBT");
        check(!TransportFare.pay(inventory, restored, true) && inventory.items.get(0).getCount() == 6,
                "unaffordable fare consumed items or accepted different NBT");
        inventory.items.set(2, new ItemStack(Items.EMERALD, 2));
        check(TransportFare.pay(inventory, restored, false) && inventory.items.get(2).getCount() == 2,
                "eligibility check must leave inventory unchanged");
        check(TransportFare.pay(inventory, restored, true) && inventory.items.get(0).isEmpty()
                && inventory.items.get(2).getCount() == 1, "fare did not consume exactly seven emeralds");
    }

    private static final class Damage {
        float health = 20, absorption = 4, healthLoss, absorptionLoss;
        boolean accepted = true;
        boolean hurt() {
            health -= healthLoss;
            absorption -= absorptionLoss;
            return accepted;
        }
    }

    private static void partTransforms() {
        var part = new PartTransform();
        float[] values = {0.5f, 1.25f, 2, -3.5f, 4.25f, -0.125f};
        for (int i = 0; i < values.length; i++) part.set(i, values[i]);
        var saved = new net.minecraft.nbt.CompoundTag();
        part.write(saved);
        var restored = new PartTransform();
        restored.read(saved);
        for (int i = 0; i < values.length; i++) check(restored.get(i) == values[i], "part transform axis lost: " + i);
        System.out.println("PART_TRANSFORM_SIX_AXIS_NBT=PASS");
    }

    public static final class PlayerVictim extends Player {
        Damage damage;
        public PlayerVictim() { super(null, BlockPos.ZERO, 0, null); }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public float getHealth() { return damage.health; }
        @Override public float getAbsorptionAmount() { return damage.absorption; }
        @Override public boolean hurt(DamageSource source, float amount) { return damage.hurt(); }
    }

    public static final class NpcVictim extends EntityNPCInterface {
        Damage damage;
        public NpcVictim() { super(null, null); }
        @Override public void performRangedAttack(LivingEntity target, float distance) {}
        @Override public float getHealth() { return damage.health; }
        @Override public float getAbsorptionAmount() { return damage.absorption; }
        @Override public boolean hurt(DamageSource source, float amount) { return damage.hurt(); }
    }

    private static void projectileDamage() {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) field.get(null);
            for (var type : List.of(PlayerVictim.class, NpcVictim.class)) {
                var victim = (LivingEntity) unsafe.allocateInstance(type);
                var damage = new Damage();
                var state = type.getDeclaredField("damage");
                state.set(victim, damage);
                check(!ProjectileExplosionBuffs.didDamage(victim, null, 5), type + ": cancelled/zero damage accepted");
                damage.healthLoss = 3;
                check(ProjectileExplosionBuffs.didDamage(victim, null, 5), type + ": health damage rejected");
                damage.healthLoss = 0;
                damage.absorptionLoss = 2;
                check(ProjectileExplosionBuffs.didDamage(victim, null, 5), type + ": absorption damage rejected");
                damage.absorptionLoss = 0;
                damage.accepted = false;
                check(!ProjectileExplosionBuffs.didDamage(victim, null, 5), type + ": rejected hit accepted");
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        System.out.println("PROJECTILE_PLAYER_NPC_DAMAGE_GATE=PASS");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
