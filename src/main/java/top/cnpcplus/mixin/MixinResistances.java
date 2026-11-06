package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import noppes.npcs.Resistances;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.stats.DamageResistanceTable;

@Mixin(value = Resistances.class, remap = false)
public abstract class MixinResistances {
    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveArray(CallbackInfoReturnable<CompoundTag> cir) {
        DamageResistanceTable.write((Resistances)(Object)this, cir.getReturnValue());
    }
    @Inject(method = "readToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readArray(CompoundTag tag, CallbackInfo ci) {
        DamageResistanceTable.read((Resistances)(Object)this, tag);
    }
    @Inject(method = "applyResistance", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$applyArray(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
        var key = source.typeHolder().unwrapKey();
        if (key.isEmpty() || !key.get().location().getNamespace().equals("minecraft")) return;
        String type = key.get().location().getPath();
        for (int i = 0; i < DamageResistanceTable.TYPES.length; i++) {
            if (DamageResistanceTable.TYPES[i].equals(type)) {
                cir.setReturnValue(Math.max(0, damage * (1 - DamageResistanceTable.values((Resistances)(Object)this)[i] / 100)));
                return;
            }
        }
    }
}
