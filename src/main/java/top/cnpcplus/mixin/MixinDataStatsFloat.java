package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = DataStats.class, remap = false)
public class MixinDataStatsFloat {

    @Shadow(remap = false)
    public int maxHealth;

    @Shadow(remap = false)
    public int healthRegen;

    @Shadow(remap = false)
    public int combatRegen;

    @Shadow(remap = false)
    public EntityNPCInterface npc;

    public float cnpcplus$getMaxHealthFloat() {
        float v = ExtraDataStorage.getFloat(this, 0);
        return v < 0.0f ? (float) maxHealth : v;
    }

    public void cnpcplus$setMaxHealthFloat(float value) {
        if (!Float.isFinite(value) || value <= 0.0f) value = 20.0f;
        ExtraDataStorage.setFloat(this, 0, value);
        maxHealth = Math.round(value);
        npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double) value);
        npc.updateClient = true;
    }

    public float cnpcplus$getHealthRegenFloat() {
        float v = ExtraDataStorage.getFloat(this, 1);
        return v < 0.0f ? (float) healthRegen : v;
    }

    public void cnpcplus$setHealthRegenFloat(float value) {
        if (!Float.isFinite(value) || value < 0.0f) value = 0.0f;
        ExtraDataStorage.setFloat(this, 1, value);
        healthRegen = Math.round(value);
    }

    public float cnpcplus$getCombatRegenFloat() {
        float v = ExtraDataStorage.getFloat(this, 2);
        return v < 0.0f ? (float) combatRegen : v;
    }

    public void cnpcplus$setCombatRegenFloat(float value) {
        if (!Float.isFinite(value) || value < 0.0f) value = 0.0f;
        ExtraDataStorage.setFloat(this, 2, value);
        combatRegen = Math.round(value);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveFloat(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound = cir.getReturnValue();
        float mh = ExtraDataStorage.getFloat(this, 0);
        if (mh >= 0.0f) compound.putFloat("CNPCPlusMaxHealth", mh);
        float hr = ExtraDataStorage.getFloat(this, 1);
        if (hr >= 0.0f) compound.putFloat("CNPCPlusHealthRegen", hr);
        float cr = ExtraDataStorage.getFloat(this, 2);
        if (cr >= 0.0f) compound.putFloat("CNPCPlusCombatRegen", cr);
    }

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readFloat(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusMaxHealth", 99)) {
            float v = compound.getFloat("CNPCPlusMaxHealth");
            if (!Float.isFinite(v) || v <= 0.0f) v = 20.0f;
            ExtraDataStorage.setFloat(this, 0, v);
            maxHealth = Math.round(v);
            npc.getAttribute(Attributes.MAX_HEALTH).setBaseValue((double) v);
        } else {
            ExtraDataStorage.setFloat(this, 0, -1.0f);
        }
        if (compound.contains("CNPCPlusHealthRegen", 99)) {
            float v = compound.getFloat("CNPCPlusHealthRegen");
            if (!Float.isFinite(v) || v < 0.0f) v = 0.0f;
            ExtraDataStorage.setFloat(this, 1, v);
            healthRegen = Math.round(v);
        } else {
            ExtraDataStorage.setFloat(this, 1, -1.0f);
        }
        if (compound.contains("CNPCPlusCombatRegen", 99)) {
            float v = compound.getFloat("CNPCPlusCombatRegen");
            if (!Float.isFinite(v) || v < 0.0f) v = 0.0f;
            ExtraDataStorage.setFloat(this, 2, v);
            combatRegen = Math.round(v);
        } else {
            ExtraDataStorage.setFloat(this, 2, -1.0f);
        }
    }

    @Inject(method = "setMaxHealth", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncMaxHealthFromInt(int value, CallbackInfo ci) {
        ExtraDataStorage.setFloat(this, 0, Math.max(1, value));
    }

    @Inject(method = "setHealthRegen", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncHealthRegenFromInt(int value, CallbackInfo ci) {
        ExtraDataStorage.setFloat(this, 1, Math.max(0, value));
    }

    @Inject(method = "setCombatRegen", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncCombatRegenFromInt(int value, CallbackInfo ci) {
        ExtraDataStorage.setFloat(this, 2, Math.max(0, value));
    }
}
