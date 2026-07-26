package bin.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(RenderCustomNpc.class)
public class RenderCustomNpcMixin {

    private static final Field SHADOW_RADIUS = findShadowRadiusField();

    private static Field findShadowRadiusField() {
        try {
            Field f = EntityRenderer.class.getDeclaredField("shadowRadius");
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to find EntityRenderer.shadowRadius", e);
        }
    }

    @Inject(method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$hideKilledModel(EntityCustomNpc entity, float $$1, float $$2, PoseStack $$3, MultiBufferSource $$4, int $$5, CallbackInfo ci) {
        if (entity.isKilled() && entity.stats.hideKilledBody && entity.deathTime > 20) {
            try {
                SHADOW_RADIUS.setFloat(this, 0.0F);
            } catch (IllegalAccessException ignored) {
            }
            ci.cancel();
            return;
        }
        LivingEntity modelEntity = entity.modelData.getEntity(entity);
        if (modelEntity != null) {
            bin.cnpcplus.util.FreezeHelper.markRenderingModelEntity(modelEntity);
        }
    }

    @Inject(method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL"), remap = false)
    private void cnpcplus$clearModelEntityFlag(EntityCustomNpc entity, float $$1, float $$2, PoseStack $$3, MultiBufferSource $$4, int $$5, CallbackInfo ci) {
        bin.cnpcplus.util.FreezeHelper.clearRenderingModelEntity();
    }
}