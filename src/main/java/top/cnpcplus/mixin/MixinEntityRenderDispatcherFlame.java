package top.cnpcplus.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * CNPC 把 hitbox=none / 隐藏尸体的 NPC 宽度压成 1.0E-5，而 renderFlame 的循环次数是
 * bbHeight / (bbWidth * 1.4 * 0.45)，会变成约 28 万次/帧 → 掉帧。这里给宽度加下限。
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcherFlame {

    @Redirect(
            method = "renderFlame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getBbWidth()F")
    )
    private float cnpcplus$clampFlameWidth(Entity entity) {
        float width = entity.getBbWidth();
        return width < 0.3f ? 0.3f : width;
    }
}
