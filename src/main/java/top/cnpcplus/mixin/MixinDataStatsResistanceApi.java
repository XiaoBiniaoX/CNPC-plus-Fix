package top.cnpcplus.mixin;

import noppes.npcs.Resistances;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.stats.DamageResistanceTable;

@Mixin(value = DataStats.class, remap = false)
public abstract class MixinDataStatsResistanceApi {
    @Shadow public Resistances resistances;
    @Shadow private EntityNPCInterface npc;
    @Unique public String[] getResistanceTypes() {
        return java.util.Arrays.stream(DamageResistanceTable.TYPES).map(type -> "minecraft:" + type).toArray(String[]::new);
    }
    @Unique public float getResistance(String type) { return DamageResistanceTable.get(resistances, type); }
    @Unique public void setResistance(String type, float percent) { DamageResistanceTable.set(resistances, type, percent); npc.updateClient = true; }
    @Unique public float[] getResistances() { return DamageResistanceTable.values(resistances).clone(); }
    @Unique public void setResistances(float[] percentages) {
        if (percentages == null || percentages.length != DamageResistanceTable.TYPES.length)
            throw new IllegalArgumentException("Expected " + DamageResistanceTable.TYPES.length + " vanilla resistance values");
        for (float value : percentages) DamageResistanceTable.validate(value);
        System.arraycopy(percentages, 0, DamageResistanceTable.values(resistances), 0, percentages.length);
        npc.updateClient = true;
    }
    @Unique public void setKnockbackResistance(float percent) { resistances.knockback = 1 + DamageResistanceTable.validate(percent) / 100; npc.updateClient = true; }
    @Unique public float getKnockbackResistance() { return (resistances.knockback - 1) * 100; }
    @Inject(method = "setResistance(IF)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$legacySet(int type, float value, CallbackInfo ci) {
        value = Math.max(0, Math.min(2, value));
        String[] types = switch (type) {
            case 0 -> new String[]{"mob_attack", "mob_attack_no_aggro", "player_attack"};
            case 1 -> new String[]{"arrow", "trident", "mob_projectile", "fireball", "unattributed_fireball", "wither_skull", "thrown", "wind_charge", "spit"};
            case 2 -> new String[]{"explosion", "player_explosion"};
            default -> new String[0];
        };
        for (String id : types) DamageResistanceTable.set(resistances, id, (value - 1) * 100);
        if (type == 0) resistances.melee = value;
        if (type == 1) resistances.arrow = value;
        if (type == 2) resistances.explosion = value;
        if (type == 3) resistances.knockback = value;
        npc.updateClient = true;
        ci.cancel();
    }
}
