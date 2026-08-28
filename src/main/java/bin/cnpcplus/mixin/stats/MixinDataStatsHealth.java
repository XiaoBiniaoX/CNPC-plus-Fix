package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.accessor.StatsFloatAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataStats.class, remap = false)
public class MixinDataStatsHealth implements StatsFloatAccess {

    @Shadow
    private EntityNPCInterface npc;

    @Unique
    private float cnpcplus$maxHealth = 20.0f;
    @Unique
    private float cnpcplus$healthRegen = 1.0f;
    @Unique
    private float cnpcplus$combatRegen = 0.0f;

    @Inject(method = "readToNBT", at = @At("HEAD"))
    private void cnpcplus$readHealthFloat(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusMaxHealth", 99)) {
            this.cnpcplus$maxHealth = compound.getFloat("CNPCPlusMaxHealth");
        } else {
            this.cnpcplus$maxHealth = compound.getInt("MaxHealth");
        }
        if (compound.contains("CNPCPlusHealthRegen", 99)) {
            this.cnpcplus$healthRegen = compound.getFloat("CNPCPlusHealthRegen");
        } else {
            this.cnpcplus$healthRegen = compound.getInt("HealthRegen");
        }
        if (compound.contains("CNPCPlusCombatRegen", 99)) {
            this.cnpcplus$combatRegen = compound.getFloat("CNPCPlusCombatRegen");
        } else {
            this.cnpcplus$combatRegen = compound.getInt("CombatRegen");
        }
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void cnpcplus$saveHealthFloat(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound.putFloat("CNPCPlusMaxHealth", this.cnpcplus$maxHealth);
        compound.putFloat("CNPCPlusHealthRegen", this.cnpcplus$healthRegen);
        compound.putFloat("CNPCPlusCombatRegen", this.cnpcplus$combatRegen);
    }

    @Override
    public float cnpcplus$getMaxHealth() {
        return this.cnpcplus$maxHealth;
    }

    @Override
    public void cnpcplus$setMaxHealth(float maxHealth) {
        if (maxHealth < 0.01f) maxHealth = 20.0f;
        ((DataStats)(Object)this).maxHealth = Math.round(maxHealth);
        this.cnpcplus$maxHealth = maxHealth;
        this.npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        this.npc.updateClient = true;
    }

    @Override
    public float cnpcplus$getHealthRegen() {
        return this.cnpcplus$healthRegen;
    }

    @Override
    public void cnpcplus$setHealthRegen(float regen) {
        if (regen < 0.0f) regen = 0.0f;
        ((DataStats)(Object)this).healthRegen = Math.round(regen);
        this.cnpcplus$healthRegen = regen;
    }

    @Override
    public float cnpcplus$getCombatRegen() {
        return this.cnpcplus$combatRegen;
    }

    @Override
    public void cnpcplus$setCombatRegen(float regen) {
        if (regen < 0.0f) regen = 0.0f;
        ((DataStats)(Object)this).combatRegen = Math.round(regen);
        this.cnpcplus$combatRegen = regen;
    }
}