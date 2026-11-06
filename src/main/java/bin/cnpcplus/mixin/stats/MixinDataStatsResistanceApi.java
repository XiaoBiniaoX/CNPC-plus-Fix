package bin.cnpcplus.mixin.stats;

import noppes.npcs.Resistances;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import bin.cnpcplus.stats.DamageResistanceTable;

@Mixin(value = DataStats.class, remap = false)
public abstract class MixinDataStatsResistanceApi {
    @Shadow public Resistances resistances;
    @Shadow private EntityNPCInterface npc;
    @Unique public String[] getResistanceTypes() { return java.util.Arrays.stream(DamageResistanceTable.TYPES).map(type -> "minecraft:" + type).toArray(String[]::new); }
    @Unique public float getResistance(String type) { return DamageResistanceTable.get(resistances, type); }
    @Unique public void setResistance(String type, float percent) { DamageResistanceTable.set(resistances, type, percent); npc.updateClient = true; }
    @Unique public float[] getResistances() { return DamageResistanceTable.values(resistances).clone(); }
    @Unique public void setResistances(float[] percentages) {
        if (percentages.length != DamageResistanceTable.TYPES.length) throw new IllegalArgumentException("Expected " + DamageResistanceTable.TYPES.length + " vanilla resistance values");
        for (float value : percentages) DamageResistanceTable.validate(value);
        System.arraycopy(percentages, 0, DamageResistanceTable.values(resistances), 0, percentages.length);
        npc.updateClient = true;
    }
    @Unique public void setKnockbackResistance(float percent) { resistances.knockback = 1 + DamageResistanceTable.validate(percent) / 100; npc.updateClient = true; }
    @Unique public float getKnockbackResistance() { return (resistances.knockback - 1) * 100; }
    @Inject(method = "getResistance(I)F", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$legacyGet(int type, CallbackInfoReturnable<Float> cir) {
        String id = type == 0 ? "mob_attack" : type == 1 ? "arrow" : type == 2 ? "explosion" : null;
        cir.setReturnValue(id != null ? 1 + DamageResistanceTable.get(resistances, id) / 100 : type == 3 ? resistances.knockback : 1);
    }
    @Inject(method = "setResistance(IF)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$legacySet(int type, float value, CallbackInfo ci) {
        value = Float.isFinite(value) ? Math.max(0, Math.min(2, value)) : 1;
        String[] types = type == 0 ? new String[]{"mob_attack", "player_attack"} : type == 1 ? new String[]{"arrow", "fireball", "thrown"} : type == 2 ? new String[]{"explosion", "player_explosion", "fireworks"} : new String[0];
        for (String id : types) DamageResistanceTable.set(resistances, id, (value - 1) * 100);
        if (type == 0) resistances.melee = value;
        if (type == 1) resistances.arrow = value;
        if (type == 2) resistances.explosion = value;
        if (type == 3) resistances.knockback = value;
        npc.updateClient = true; ci.cancel();
    }
}
