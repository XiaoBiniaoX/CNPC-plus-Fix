package bin.cnpcplus.mixin.stats;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import bin.cnpcplus.common.IDataStatsFloatAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataStats.class, remap = false)
public abstract class MixinDataStatsFloat implements IDataStatsFloatAccess {

    @Shadow(remap = false)
    public int maxHealth;

    @Shadow(remap = false)
    public int healthRegen;

    @Shadow(remap = false)
    public int combatRegen;

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Unique
    private float cnpcplus$maxHealth = 20.0f;

    @Unique
    private float cnpcplus$healthRegen = 1.0f;

    @Unique
    private float cnpcplus$combatRegen = 0.0f;

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcplus$onReadToNBT(NBTTagCompound compound, CallbackInfo ci) {
        if (compound.hasKey("CNPCPlusMaxHealth", 99)) {
            cnpcplus$maxHealth = cnpcplus$validPositive(compound.getFloat("CNPCPlusMaxHealth"), 20.0f);
            cnpcplus$setMaxHealthFloat(cnpcplus$maxHealth);
        } else {
            cnpcplus$maxHealth = (float) this.maxHealth;
        }
        cnpcplus$healthRegen = compound.hasKey("CNPCPlusHealthRegen", 99) ? cnpcplus$validNonNegative(compound.getFloat("CNPCPlusHealthRegen")) : (float) this.healthRegen;
        cnpcplus$combatRegen = compound.hasKey("CNPCPlusCombatRegen", 99) ? cnpcplus$validNonNegative(compound.getFloat("CNPCPlusCombatRegen")) : (float) this.combatRegen;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$onWriteToNBT(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        compound.setFloat("CNPCPlusMaxHealth", cnpcplus$maxHealth);
        compound.setFloat("CNPCPlusHealthRegen", cnpcplus$healthRegen);
        compound.setFloat("CNPCPlusCombatRegen", cnpcplus$combatRegen);
    }

    @Override
    public void cnpcplus$setMaxHealthFloat(float health) {
        health = cnpcplus$validPositive(health, 20.0f);
        cnpcplus$maxHealth = health;
        this.maxHealth = Math.round(health);
        this.npc.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue((double) health);
        this.npc.setHealth(this.npc.getMaxHealth());
        this.npc.updateClient = true;
    }

    @Override
    public float cnpcplus$getMaxHealthFloat() {
        return cnpcplus$maxHealth;
    }

    @Inject(method = "setMaxHealth", at = @At("RETURN"))
    private void cnpcplus$syncMaxHealthFromInt(int health, CallbackInfo ci) {
        cnpcplus$maxHealth = cnpcplus$validPositive(health, 20.0f);
    }

    @Inject(method = "setHealthRegen", at = @At("RETURN"))
    private void cnpcplus$syncHealthRegenFromInt(int regen, CallbackInfo ci) {
        cnpcplus$healthRegen = cnpcplus$validNonNegative(regen);
    }

    @Inject(method = "setCombatRegen", at = @At("RETURN"))
    private void cnpcplus$syncCombatRegenFromInt(int regen, CallbackInfo ci) {
        cnpcplus$combatRegen = cnpcplus$validNonNegative(regen);
    }

    @Override
    public void cnpcplus$setHealthRegenFloat(float regen) {
        regen = cnpcplus$validNonNegative(regen);
        cnpcplus$healthRegen = regen;
        this.healthRegen = Math.round(regen);
    }

    @Override
    public float cnpcplus$getHealthRegenFloat() {
        return cnpcplus$healthRegen;
    }

    @Override
    public void cnpcplus$setCombatRegenFloat(float regen) {
        regen = cnpcplus$validNonNegative(regen);
        cnpcplus$combatRegen = regen;
        this.combatRegen = Math.round(regen);
    }

    @Override
    public float cnpcplus$getCombatRegenFloat() {
        return cnpcplus$combatRegen;
    }

    @Unique
    private static float cnpcplus$validPositive(float value, float fallback) {
        return Float.isFinite(value) && value > 0.0f ? value : fallback;
    }

    @Unique
    private static float cnpcplus$validNonNegative(float value) {
        return Float.isFinite(value) && value >= 0.0f ? value : 0.0f;
    }
}
