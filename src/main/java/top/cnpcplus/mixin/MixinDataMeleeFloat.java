package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeFloat {

    @Shadow(remap = false)
    private int attackStrength;

    @Shadow(remap = false)
    private int attackSpeed;

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    public float cnpcplus$getStrengthFloat() {
        float v = ExtraDataStorage.getFloat(this, 3);
        return v < 0.0f ? (float) attackStrength : v;
    }

    public void cnpcplus$setStrengthFloat(float value) {
        if (!Float.isFinite(value) || value < 0.0f) value = 0.0f;
        ExtraDataStorage.setFloat(this, 3, value);
        attackStrength = Math.round(value);
        npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((double) value);
    }

    public float cnpcplus$getDelayFloat() {
        float v = ExtraDataStorage.getFloat(this, 4);
        return v < 0.0f ? (float) attackSpeed : v;
    }

    public void cnpcplus$setDelayFloat(float value) {
        if (!Float.isFinite(value) || value <= 0.0f) value = 1.0f;
        ExtraDataStorage.setFloat(this, 4, value);
        attackSpeed = Math.round(value);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveFloat(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound = cir.getReturnValue();
        float as = ExtraDataStorage.getFloat(this, 3);
        if (as >= 0.0f) compound.putFloat("CNPCPlusAttackStrength", as);
        float aspd = ExtraDataStorage.getFloat(this, 4);
        if (aspd >= 0.0f) compound.putFloat("CNPCPlusAttackSpeed", aspd);
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpcplus$readFloat(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusAttackStrength", 99)) {
            float v = compound.getFloat("CNPCPlusAttackStrength");
            if (!Float.isFinite(v) || v < 0.0f) v = 0.0f;
            ExtraDataStorage.setFloat(this, 3, v);
            attackStrength = Math.round(v);
        } else {
            ExtraDataStorage.setFloat(this, 3, -1.0f);
        }
        if (compound.contains("CNPCPlusAttackSpeed", 99)) {
            float v = compound.getFloat("CNPCPlusAttackSpeed");
            if (!Float.isFinite(v) || v <= 0.0f) v = 1.0f;
            ExtraDataStorage.setFloat(this, 4, v);
            attackSpeed = Math.round(v);
        } else {
            ExtraDataStorage.setFloat(this, 4, -1.0f);
        }
    }

    @Inject(method = "setStrength", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncStrengthFromInt(int value, CallbackInfo ci) {
        ExtraDataStorage.setFloat(this, 3, Math.max(0, value));
    }

    @Inject(method = "setDelay", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncDelayFromInt(int value, CallbackInfo ci) {
        ExtraDataStorage.setFloat(this, 4, Math.max(1, value));
    }
}
