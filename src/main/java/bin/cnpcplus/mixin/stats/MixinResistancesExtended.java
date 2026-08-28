package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IResistanceExtendedAccess;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import noppes.npcs.Resistances;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Resistances.class, remap = false)
public abstract class MixinResistancesExtended implements IResistanceExtendedAccess {

    @Unique
    private float cnpcplus$genericDamage = 1.0f;

    @Unique
    private float cnpcplus$magicDamage = 1.0f;

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void cnpcplus$onWriteToNBT(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound compound = cir.getReturnValue();
        compound.setFloat("CNPCPlusGenericDamage", cnpcplus$genericDamage);
        compound.setFloat("CNPCPlusMagicDamage", cnpcplus$magicDamage);
    }

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcplus$onReadToNBT(NBTTagCompound compound, CallbackInfo ci) {
        cnpcplus$genericDamage = cnpcplus$valid(compound.hasKey("CNPCPlusGenericDamage", 99) ? compound.getFloat("CNPCPlusGenericDamage") : 1.0f);
        cnpcplus$magicDamage = cnpcplus$valid(compound.hasKey("CNPCPlusMagicDamage", 99) ? compound.getFloat("CNPCPlusMagicDamage") : 1.0f);
    }

    @Inject(method = "applyResistance", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onApplyResistance(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
        if (source == null || !Float.isFinite(damage)) {
            return;
        }
        String type = source.damageType;
        if ("generic".equals(type) || "cramming".equals(type) || "anvil".equals(type) || "cactus".equals(type)) {
            cir.setReturnValue(damage * (2.0f - cnpcplus$genericDamage));
            return;
        }
        if ("indirectMagic".equals(type) || "thorns".equals(type) || "magic".equals(type) || "lightningBolt".equals(type) || "genericKill".equals(type)) {
            cir.setReturnValue(damage * (2.0f - cnpcplus$magicDamage));
        }
    }

    @Override
    public float cnpcplus$getGenericDamage() {
        return cnpcplus$genericDamage;
    }

    @Override
    public void cnpcplus$setGenericDamage(float value) {
        cnpcplus$genericDamage = cnpcplus$valid(value);
    }

    @Override
    public float cnpcplus$getMagicDamage() {
        return cnpcplus$magicDamage;
    }

    @Override
    public void cnpcplus$setMagicDamage(float value) {
        cnpcplus$magicDamage = cnpcplus$valid(value);
    }

    @Unique
    private static float cnpcplus$valid(float value) {
        return Float.isFinite(value) && value >= 0.0f ? Math.min(value, 2.0f) : 1.0f;
    }
}
