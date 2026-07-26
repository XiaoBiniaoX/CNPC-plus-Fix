package bin.cnpcplus.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

/**
 * Undead Revamped 尸体隐藏修复：
 * 1. onDeath 后初始化 deathTime=1
 * 2. preTick 前后 save/restore deathTime，防止 EpicFight 减 1
 * 3. UndeadRevampedTickMixin 在 tick() 尾部对 deathTime>0 且 health>0 的实体手动调用 tickDeath()
 */
@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch", remap = false)
public abstract class UndeadRevampedDeathFixMixin {

    @Unique private int cnpcplus$savedDeathTime;

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

    @Unique
    private static boolean cnpcplus$isUndeadRevamped(LivingEntity entity) {
        return entity.getClass().getName().startsWith("net.mcreator.undeadrevamp");
    }

    @Inject(method = "onDeath", at = @At("TAIL"), remap = false)
    private void cnpcplus$initDeathTime(DamageSource damageSource, CallbackInfo ci) {
        Object original = cnpcplus$getOriginal(this);
        if (original instanceof LivingEntity living && cnpcplus$isUndeadRevamped(living) && living.deathTime == 0) {
            living.deathTime = 1;
        }
    }

    @Inject(method = "preTick", at = @At("HEAD"), remap = false)
    private void cnpcplus$saveDeathTime(CallbackInfo ci) {
        Object original = cnpcplus$getOriginal(this);
        if (original instanceof LivingEntity living && cnpcplus$isUndeadRevamped(living)) {
            cnpcplus$savedDeathTime = living.deathTime;
        }
    }

    @Inject(method = "preTick", at = @At("TAIL"), remap = false)
    private void cnpcplus$restoreDeathTime(CallbackInfo ci) {
        Object original = cnpcplus$getOriginal(this);
        if (original instanceof LivingEntity living && cnpcplus$isUndeadRevamped(living)) {
            living.deathTime = cnpcplus$savedDeathTime;
        }
    }
}