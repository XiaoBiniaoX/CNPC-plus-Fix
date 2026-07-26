package bin.cnpcplus.mixin;

import net.minecraft.world.damagesource.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch", remap = false)
public abstract class LivingEntityPatchDeathGuardMixin {

    @Unique private boolean cnpcplus$deathHandled;

    @Unique
    private static Object cnpcplus$getOriginal(Object patch) {
        try {
            for (Class<?> c = patch.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Method m = c.getDeclaredMethod("getOriginal");
                    m.setAccessible(true);
                    return m.invoke(patch);
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$guardDeathReentry(DamageSource damageSource, CallbackInfo ci) {
        if (cnpcplus$getOriginal(this) instanceof EntityNPCInterface) {
            if (cnpcplus$deathHandled) {
                ci.cancel();
                return;
            }
            cnpcplus$deathHandled = true;
        }
    }
}