package top.cnpcplus.drop.fx.client;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** 客户端粒子图标查询；不暴露原版私有 MutableSpriteSet 类型。 */
public interface ParticleEngineSpriteAccess {
    Map<ResourceLocation, ?> cnpcplus$getSpriteSets();
}
