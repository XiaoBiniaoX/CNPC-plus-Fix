package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import top.cnpcplus.shader.client.ClientShaders;

@Mixin(EntityRenderDispatcher.class)
public class MixinShaderEntityRenderer {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"), require = 1)
    private void cnpcplus$object(EntityRenderer<Entity> renderer, Entity entity, float yaw, float partial, PoseStack poses, MultiBufferSource source, int light) {
        MultiBufferSource wrapped = ClientShaders.wrap("entity:" + entity.getUUID(), source, partial);
        try { renderer.render(entity, yaw, partial, poses, wrapped, light); }
        finally { ClientShaders.finish(wrapped); }
    }
}
