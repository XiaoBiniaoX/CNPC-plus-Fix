package bin.cnpcplus.mixin.drop;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.cnpcplus.drop.fx.client.ParticleEngineSpriteAccess;
import java.util.Map;

@Mixin(value = ParticleEngine.class, remap = false)
public interface ParticleEngineSpriteAccessor extends ParticleEngineSpriteAccess {
    @Override @Accessor("spriteSets")
    Map<ResourceLocation, ?> cnpcplus$getSpriteSets();
}
