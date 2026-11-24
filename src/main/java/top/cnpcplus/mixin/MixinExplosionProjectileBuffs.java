package top.cnpcplus.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ranged.ProjectileExplosionBuffs;

@Mixin(value = Explosion.class, remap = false)
public abstract class MixinExplosionProjectileBuffs {
    @Inject(method = "explode", at = @At("HEAD"), require = 1)
    private void cnpcplus$bindProjectileExplosion(CallbackInfo ci) {
        var impact = ProjectileExplosionBuffs.ACTIVE.get();
        if (impact != null && impact.explosion == null) impact.explosion = (Explosion) (Object) this;
    }

    @Redirect(method = "explode", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"), require = 1)
    private boolean cnpcplus$trackExplosionDamage(Entity entity, DamageSource source, float amount) {
        var impact = ProjectileExplosionBuffs.ACTIVE.get();
        if (impact != null && impact.explosion == (Object) this && entity instanceof LivingEntity living) {
            boolean damaged = ProjectileExplosionBuffs.didDamage(living, source, amount);
            if (damaged) impact.damaged.add(living);
            return damaged;
        }
        return entity.hurt(source, amount);
    }
}
