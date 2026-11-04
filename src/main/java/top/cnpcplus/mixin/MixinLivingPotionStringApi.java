package top.cnpcplus.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityLivingBaseWrapper.class, remap = false)
public abstract class MixinLivingPotionStringApi {
    @Inject(method = "addPotionEffect(Ljava/lang/String;IIZ)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$stringPotion(String name, int duration, int strength, boolean hideParticles, CallbackInfo ci) {
        ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name.trim());
        var effect = id == null ? null : net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect == null) throw new noppes.npcs.api.CustomNPCsException("Unknown potion effect: " + name);
        var entity = (net.minecraft.world.entity.LivingEntity)((EntityLivingBaseWrapper<?>)(Object)this).getMCEntity();
        if (duration <= 0) entity.removeEffect(effect);
        else entity.addEffect(new MobEffectInstance(effect, effect.isInstantenous() ? duration : Math.min(duration, 1000000) * 20,
                Math.max(0, Math.min(255, strength)), false, !hideParticles, !hideParticles));
        ci.cancel();
    }
}
