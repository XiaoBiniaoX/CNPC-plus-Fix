package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.melee.MeleeFloatValues;

@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeFloat implements MeleeFloatValues {

    @Shadow(remap = false)
    private int attackStrength;

    @Shadow(remap = false)
    private int attackSpeed;

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Unique private float cnpcplus$strength = -1.0f;
    @Unique private float cnpcplus$delay = -1.0f;

    @Override public float cnpcplus$getStrengthFloat() {
        return cnpcplus$strength < 0.0f ? attackStrength : cnpcplus$strength;
    }

    @Override public void cnpcplus$setStrengthFloat(float value) {
        if (!Float.isFinite(value) || value < 0.0f) value = 0.0f;
        cnpcplus$strength = value;
        attackStrength = Math.round(value);
        npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue((double) value);
    }

    @Override public float cnpcplus$getDelayFloat() {
        return cnpcplus$delay < 0.0f ? attackSpeed : cnpcplus$delay;
    }

    @Override public void cnpcplus$setDelayFloat(float value) {
        if (!Float.isFinite(value) || value <= 0.0f) value = 1.0f;
        cnpcplus$delay = value;
        attackSpeed = Math.max(1, Math.round(value));
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveFloat(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound = cir.getReturnValue();
        if (cnpcplus$strength >= 0.0f) compound.putFloat("CNPCPlusAttackStrength", cnpcplus$strength);
        if (cnpcplus$delay >= 0.0f) compound.putFloat("CNPCPlusAttackSpeed", cnpcplus$delay);
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpcplus$readFloat(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusAttackStrength", 99)) {
            cnpcplus$setStrengthFloat(compound.getFloat("CNPCPlusAttackStrength"));
        } else {
            cnpcplus$strength = -1.0f;
        }
        if (compound.contains("CNPCPlusAttackSpeed", 99)) {
            cnpcplus$setDelayFloat(compound.getFloat("CNPCPlusAttackSpeed"));
        } else {
            cnpcplus$delay = -1.0f;
        }
    }

    /**
     * 整数入口的语义 = 「调用方放弃小数精度」，所以这里无条件把小数槽拉回整数值。
     * 脚本走 INPCMelee.setStrength(int) 时必须如此，否则脚本改完会被旧小数回弹。
     */
    @Inject(method = "setStrength", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncStrengthFromInt(int value, CallbackInfo ci) {
        cnpcplus$strength = Math.max(0, value);
    }

    @Inject(method = "setDelay", at = @At("RETURN"), remap = false)
    private void cnpcplus$syncDelayFromInt(int value, CallbackInfo ci) {
        cnpcplus$delay = Math.max(1, value);
    }
}
