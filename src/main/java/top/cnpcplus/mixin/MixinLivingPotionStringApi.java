package top.cnpcplus.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = EntityLivingBaseWrapper.class, remap = false)
public abstract class MixinLivingPotionStringApi {
    // This CNPC build only declares the integer overload; add the accepted string contract directly.
    @Unique public void addPotionEffect(String name, int duration, int strength, boolean hideParticles) {
        ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name.trim());
        var effect = id == null ? null : BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
        if (effect == null) throw new noppes.npcs.api.CustomNPCsException("Unknown potion effect: " + name);
        LivingEntity entity = ((EntityLivingBaseWrapper<?>)(Object)this).getMCEntity();
        if (duration <= 0) entity.removeEffect(effect);
        else entity.addEffect(new MobEffectInstance(effect, effect.value().isInstantenous() ? duration : Math.min(duration, 1000000) * 20,
                Math.max(0, Math.min(255, strength)), false, !hideParticles, !hideParticles));
    }
}
