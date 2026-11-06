package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(RenderManager.class)
public abstract class MixinRenderEntityTarget {
    @Redirect(method = "renderEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/Render;doRender(Lnet/minecraft/entity/Entity;DDDFF)V"))
    private void cnpcplus$target(Render<Entity> renderer, Entity entity, double x, double y, double z, float yaw, float partial) {
        ClientShaders.pushTarget("entity:" + entity.getUniqueID());
        try { renderer.doRender(entity, x, y, z, yaw, partial); } finally { ClientShaders.popTarget(); }
    }
}
