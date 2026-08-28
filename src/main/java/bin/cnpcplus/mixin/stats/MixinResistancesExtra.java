package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.accessor.ResistExtraAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import noppes.npcs.Resistances;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Resistances.class, remap = false)
public class MixinResistancesExtra implements ResistExtraAccess {

    @Unique
    public float cnpcplus$genericDamage = 1.0f;
    @Unique
    public float cnpcplus$magicDamage = 1.0f;

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcplus$readExtra(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusGenericDamage", 99)) {
            this.cnpcplus$genericDamage = compound.getFloat("CNPCPlusGenericDamage");
        }
        if (compound.contains("CNPCPlusMagicDamage", 99)) {
            this.cnpcplus$magicDamage = compound.getFloat("CNPCPlusMagicDamage");
        }
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveExtra(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putFloat("CNPCPlusGenericDamage", this.cnpcplus$genericDamage);
        tag.putFloat("CNPCPlusMagicDamage", this.cnpcplus$magicDamage);
    }

    @Inject(method = "applyResistance", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$applyResistanceExtra(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
        String msgId = source.getMsgId();

        if (msgId.equals("generic") || msgId.equals("cramming") || msgId.equals("anvil") || msgId.equals("cactus")) {
            cir.setReturnValue(damage * (2.0f - this.cnpcplus$genericDamage));
            return;
        }

        if (msgId.equals("indirectMagic") || msgId.equals("sonicBoom") || msgId.equals("outOfBorder")
                || msgId.equals("thorns") || msgId.equals("magic") || msgId.equals("lightningBolt")
                || msgId.equals("genericKill")) {
            cir.setReturnValue(damage * (2.0f - this.cnpcplus$magicDamage));
            return;
        }
    }

    @Override
    public float cnpcplus$getGenericDamage() {
        return this.cnpcplus$genericDamage;
    }

    @Override
    public void cnpcplus$setGenericDamage(float val) {
        this.cnpcplus$genericDamage = val;
    }

    @Override
    public float cnpcplus$getMagicDamage() {
        return this.cnpcplus$magicDamage;
    }

    @Override
    public void cnpcplus$setMagicDamage(float val) {
        this.cnpcplus$magicDamage = val;
    }
}