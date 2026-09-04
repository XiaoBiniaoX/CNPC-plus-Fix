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

    @Unique
    private float cnpcplus$attackStrength = 5.0f;
    @Unique
    private float cnpcplus$attackSpeed = 20.0f;

    @Inject(method = "load", at = @At("HEAD"))
    private void cnpcplus$loadMeleeFloat(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusAttackStrength", 99)) {
            this.cnpcplus$attackStrength = compound.getFloat("CNPCPlusAttackStrength");
        } else {
            this.cnpcplus$attackStrength = compound.getInt("AttackStrenght");
        }
        if (compound.contains("CNPCPlusAttackSpeed", 99)) {
            this.cnpcplus$attackSpeed = compound.getFloat("CNPCPlusAttackSpeed");
        } else {
            this.cnpcplus$attackSpeed = compound.getInt("AttackSpeed");
        }
    }

    @Inject(method = "setStrength", at = @At("RETURN"))
    private void cnpcplus$syncIntMeleeFloat(int strength, CallbackInfo ci) {
        // 脚本API调用 setStrength(int) 时同步影子值和 attribute，避免残留旧浮点值
        this.cnpcplus$attackStrength = strength;
        this.npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(strength);
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void cnpcplus$saveMeleeFloat(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound.putFloat("CNPCPlusAttackStrength", this.cnpcplus$attackStrength);
        compound.putFloat("CNPCPlusAttackSpeed", this.cnpcplus$attackSpeed);
    }

    @Override
    public float cnpcplus$getStrength() {
        return this.cnpcplus$attackStrength;
    }

    @Override
    public void cnpcplus$setStrength(float strength) {
        if (strength < 0.0f) strength = 0.0f;
        ((DataMelee)(Object)this).setStrength(Math.round(strength));
        this.cnpcplus$attackStrength = strength;
        this.npc.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(strength);
    }

    @Override
    public float cnpcplus$getDelay() {
        return this.cnpcplus$attackSpeed;
    }

    @Override
    public void cnpcplus$setDelay(float speed) {
        if (speed < 0.01f) speed = 1.0f;
        ((DataMelee)(Object)this).setDelay(Math.round(speed));
        this.cnpcplus$attackSpeed = speed;
    }
}