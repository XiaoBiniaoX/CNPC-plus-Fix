package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import noppes.npcs.Resistances;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.ExtraDataStorage;

@Mixin(value = Resistances.class, remap = false)
public class MixinResistances {

    @Shadow public float knockback;
    @Shadow public float arrow;
    @Shadow public float melee;
    @Shadow public float explosion;

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveExt(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag compound = cir.getReturnValue();
        float g = ExtraDataStorage.getFloat(this, 5);
        if (g >= 0.0f) compound.putFloat("CNPCPlusGeneric", cnpcplus$valid(g));
        float m = ExtraDataStorage.getFloat(this, 6);
        if (m >= 0.0f) compound.putFloat("CNPCPlusMagic", cnpcplus$valid(m));
    }

    @Inject(method = "readToNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$readExt(CompoundTag compound, CallbackInfo ci) {
        ExtraDataStorage.setFloat(this, 5, cnpcplus$valid(compound.contains("CNPCPlusGeneric", 99) ? compound.getFloat("CNPCPlusGeneric") : 1.0f));
        ExtraDataStorage.setFloat(this, 6, cnpcplus$valid(compound.contains("CNPCPlusMagic", 99) ? compound.getFloat("CNPCPlusMagic") : 1.0f));
    }

    @Inject(method = "applyResistance", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$applyResistance(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
        String msgId = source.getMsgId();
        if (msgId.equals("generic") || msgId.equals("cramming") || msgId.equals("anvil") || msgId.equals("cactus")) {
            cir.setReturnValue(damage * (2.0f - cnpcplus$valid(ExtraDataStorage.getFloat(this, 5))));
        } else if (msgId.equals("indirectMagic") || msgId.equals("sonic_boom") || msgId.equals("outOfBorder") || msgId.equals("thorns") || msgId.equals("magic") || msgId.equals("lightningBolt") || msgId.equals("genericKill")) {
            cir.setReturnValue(damage * (2.0f - cnpcplus$valid(ExtraDataStorage.getFloat(this, 6))));
        }
    }

    private static float cnpcplus$valid(float value) {
        if (!Float.isFinite(value) || value < 0.0f) return 1.0f;
        return Math.min(value, 2.0f);
    }
}
