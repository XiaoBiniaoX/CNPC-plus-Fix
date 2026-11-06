package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IResistanceExtendedAccess;
import bin.cnpcplus.stats.DamageResistanceTable;
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
        DamageResistanceTable.write((Resistances)(Object)this, compound);
    }

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcplus$onReadToNBT(NBTTagCompound compound, CallbackInfo ci) {
        cnpcplus$genericDamage = cnpcplus$valid(compound.hasKey("CNPCPlusGenericDamage", 99) ? compound.getFloat("CNPCPlusGenericDamage") : 1.0f);
        cnpcplus$magicDamage = cnpcplus$valid(compound.hasKey("CNPCPlusMagicDamage", 99) ? compound.getFloat("CNPCPlusMagicDamage") : 1.0f);
        DamageResistanceTable.read((Resistances)(Object)this, compound);
    }

    @Inject(method = "applyResistance", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onApplyResistance(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
        if (source == null || !Float.isFinite(damage)) {
            return;
        }
        int index = DamageResistanceTable.source(source);
        float percent = index >= 0 ? DamageResistanceTable.values((Resistances)(Object)this)[index]
                : "genericKill".equals(source.damageType) ? DamageResistanceTable.get((Resistances)(Object)this, "magic") : 0;
        cir.setReturnValue(Math.max(0, damage * (1 - percent / 100)));
    }

    @Override
    public float cnpcplus$getGenericDamage() {
        return 1 + DamageResistanceTable.get((Resistances)(Object)this, "generic") / 100;
    }

    @Override
    public void cnpcplus$setGenericDamage(float value) {
        cnpcplus$genericDamage = cnpcplus$valid(value);
        for (String type : new String[]{"generic", "cramming", "falling_anvil", "cactus"}) DamageResistanceTable.set((Resistances)(Object)this, type, (cnpcplus$genericDamage - 1) * 100);
    }

    @Override
    public float cnpcplus$getMagicDamage() {
        return 1 + DamageResistanceTable.get((Resistances)(Object)this, "magic") / 100;
    }

    @Override
    public void cnpcplus$setMagicDamage(float value) {
        cnpcplus$magicDamage = cnpcplus$valid(value);
        for (String type : new String[]{"indirect_magic", "thorns", "magic", "lightning_bolt"}) DamageResistanceTable.set((Resistances)(Object)this, type, (cnpcplus$magicDamage - 1) * 100);
    }

    @Unique
    private static float cnpcplus$valid(float value) {
        return Float.isFinite(value) && value >= 0.0f ? Math.min(value, 2.0f) : 1.0f;
    }
}
