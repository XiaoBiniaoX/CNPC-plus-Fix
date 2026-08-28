package bin.cnpcplus.mixin.stats;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataMelee;
import bin.cnpcplus.common.IDataMeleeFloatAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataMelee.class, remap = false)
public abstract class MixinDataMeleeFloat implements IDataMeleeFloatAccess {

    @Shadow(remap = false)
    private int attackStrength;

    @Shadow(remap = false)
    private int attackSpeed;

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Unique
    private float cnpcplus$attackStrength = 5.0f;

    @Unique
    private float cnpcplus$attackSpeed = 20.0f;

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void cnpcplus$onReadFromNBT(NBTTagCompound compound, CallbackInfo ci) {
        cnpcplus$attackStrength = compound.hasKey("CNPCPlusAttackStrength", 99) ? cnpcplus$validStrength(compound.getFloat("CNPCPlusAttackStrength")) : (float) this.attackStrength;
        cnpcplus$attackSpeed = compound.hasKey("CNPCPlusAttackSpeed", 99) ? cnpcplus$validSpeed(compound.getFloat("CNPCPlusAttackSpeed")) : (float) this.attackSpeed;
        cnpcplus$applyStrengthAttribute();
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$onWriteToNBT(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        compound.setFloat("CNPCPlusAttackStrength", cnpcplus$attackStrength);
        compound.setFloat("CNPCPlusAttackSpeed", cnpcplus$attackSpeed);
    }

    @Unique
    private void cnpcplus$applyStrengthAttribute() {
        this.npc.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue((double) cnpcplus$attackStrength);
    }

    @Inject(method = "setStrength", at = @At("RETURN"))
    private void cnpcplus$syncStrengthFromInt(int strength, CallbackInfo ci) {
        cnpcplus$attackStrength = cnpcplus$validStrength(strength);
    }

    @Inject(method = "setDelay", at = @At("RETURN"))
    private void cnpcplus$syncDelayFromInt(int speed, CallbackInfo ci) {
        cnpcplus$attackSpeed = cnpcplus$validSpeed(speed);
    }

    @Override
    public float cnpcplus$getStrengthFloat() {
        return cnpcplus$attackStrength;
    }

    @Override
    public void cnpcplus$setStrengthFloat(float strength) {
        strength = cnpcplus$validStrength(strength);
        cnpcplus$attackStrength = strength;
        this.attackStrength = Math.round(strength);
        cnpcplus$applyStrengthAttribute();
    }

    @Override
    public float cnpcplus$getDelayFloat() {
        return cnpcplus$attackSpeed;
    }

    @Override
    public void cnpcplus$setDelayFloat(float speed) {
        speed = cnpcplus$validSpeed(speed);
        cnpcplus$attackSpeed = speed;
        this.attackSpeed = Math.max(1, Math.round(speed));
    }

    @Unique
    private static float cnpcplus$validStrength(float value) {
        return Float.isFinite(value) && value >= 0.0f ? value : 0.0f;
    }

    @Unique
    private static float cnpcplus$validSpeed(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 1.0f;
    }
}
