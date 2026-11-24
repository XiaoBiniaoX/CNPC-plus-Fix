package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.common.ExplosionDamageEffects;
import java.util.function.Consumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Explosion.class)
public abstract class MixinExplosionDamageEffects {
    @Redirect(method = "doExplosionA", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;attackEntityFrom(Lnet/minecraft/util/DamageSource;F)Z"), require = 1)
    private boolean cnpcplus$afterDamage(Entity target, DamageSource source, float amount) {
        boolean success = target.attackEntityFrom(source, amount);
        Consumer<EntityLivingBase> effect = ExplosionDamageEffects.CURRENT.get();
        if (success && effect != null && target instanceof EntityLivingBase) effect.accept((EntityLivingBase)target);
        return success;
    }
}
