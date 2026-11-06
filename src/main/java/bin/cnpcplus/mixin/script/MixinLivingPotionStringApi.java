package bin.cnpcplus.mixin.script;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.api.wrapper.EntityLivingBaseWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** The legacy wrapper has only the numeric overload; expose the modern string signature directly. */
@Mixin(value = EntityLivingBaseWrapper.class, remap = false)
public abstract class MixinLivingPotionStringApi {
    @Unique public void addPotionEffect(String name, int duration, int strength, boolean hideParticles) {
        if (name == null || !name.trim().matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) throw new noppes.npcs.api.CustomNPCsException("Unknown potion effect: " + name);
        Potion effect = net.minecraftforge.fml.common.registry.ForgeRegistries.POTIONS.getValue(new ResourceLocation(name.trim()));
        if (effect == null) throw new noppes.npcs.api.CustomNPCsException("Unknown potion effect: " + name);
        EntityLivingBase entity = (EntityLivingBase)((EntityLivingBaseWrapper<?>)(Object)this).getMCEntity();
        if (duration <= 0) entity.removePotionEffect(effect);
        else entity.addPotionEffect(new PotionEffect(effect, effect.isInstant() ? duration : Math.min(duration, 1000000) * 20,
                Math.max(0, Math.min(255, strength)), false, !hideParticles));
    }
}
