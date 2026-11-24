package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.accessor.MeleeFloatAccess;
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

@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeFloat implements MeleeFloatAccess {

    @Shadow
    private EntityNPCInterface npc;

    @Shadow private int attackStrength;
    @Shadow private int attackSpeed;

    @Unique
    private float cnpcplus$attackStrength = -1.0f;
    @Unique
    private float cnpcplus$attackSpeed = -1.0f;

    @Inject(method = "load", at = @At("RETURN"), require = 1)
    private void cnpcplus$loadMeleeFloat(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusAttackStrength", 99)) {
            cnpcplus$setStrength(compound.getFloat("CNPCPlusAttackStrength"));
        } else {
            this.cnpcplus$attackStrength = -1.0f;
        }
        if (compound.contains("CNPCPlusAttackSpeed", 99)) {
            cnpcplus$setDelay(compound.getFloat("CNPCPlusAttackSpeed"));
        } else {
            this.cnpcplus$attackSpeed = -1.0f;
        }
    }

    @Inject(method = "setStrength", at = @At("RETURN"), require = 1)
    private void cnpcplus$syncIntMeleeFloat(int strength, CallbackInfo ci) {
        this.cnpcplus$attackStrength = Math.max(0, strength);
    }

    @Inject(method = "setDelay", at = @At("RETURN"), require = 1)
    private void cnpcplus$syncIntDelay(int speed, CallbackInfo ci) {
        this.cnpcplus$attackSpeed = Math.max(1, speed);
    }

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveMeleeFloat(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound = cir.getReturnValue();
        if (this.cnpcplus$attackStrength >= 0.0f) compound.putFloat("CNPCPlusAttackStrength", this.cnpcplus$attackStrength);
        if (this.cnpcplus$attackSpeed >= 0.0f) compound.putFloat("CNPCPlusAttackSpeed", this.cnpcplus$attackSpeed);
    }

    @Override
    public float cnpcplus$getStrength() {
        return this.cnpcplus$attackStrength < 0.0f ? attackStrength : this.cnpcplus$attackStrength;
    }

    @Override
    public void cnpcplus$setStrength(float strength) {
        if (!Float.isFinite(strength) || strength < 0.0f) strength = 0.0f;
        this.cnpcplus$attackStrength = strength;
        attackStrength = Math.round(strength);
        this.npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(strength);
    }

    @Override
    public float cnpcplus$getDelay() {
        return this.cnpcplus$attackSpeed < 0.0f ? attackSpeed : this.cnpcplus$attackSpeed;
    }

    @Override
    public void cnpcplus$setDelay(float speed) {
        if (!Float.isFinite(speed) || speed <= 0.0f) speed = 1.0f;
        this.cnpcplus$attackSpeed = speed;
        attackSpeed = Math.max(1, Math.round(speed));
    }
}
