package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.layer.LayerParts;
import noppes.npcs.client.parts.MpmPartAbstractClient;
import noppes.npcs.client.parts.MpmPartData;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.client.NpcAlphaVertexConsumer;
import top.cnpcplus.model.PartTransform;

@Mixin(value = LayerParts.class, remap = false)
public abstract class MixinLayerPartsNpcAlpha {
    @Redirect(method = "renderPart", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/parts/MpmPartAbstractClient;render(Lnoppes/npcs/client/parts/MpmPartData;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;)V"), require = 5)
    private static void cnpcplus$renderPart(MpmPartAbstractClient part, MpmPartData data, PoseStack pose,
                                          MultiBufferSource buffers, int light, LivingEntity entity) {
        MultiBufferSource output = entity instanceof EntityNPCInterface npc && npc.display.getVisible() == 2
                ? type -> new NpcAlphaVertexConsumer(buffers.getBuffer(type)) : buffers;
        PartTransform transform = ((PartTransform.Access) data).cnpcplus$partTransform();
        if (transform.isIdentity()) {
            part.render(data, pose, output, light, entity);
            return;
        }
        pose.pushPose();
        try {
            pose.translate(transform.get(3) / 16f, transform.get(4) / 16f, transform.get(5) / 16f);
            pose.scale(transform.get(0), transform.get(1), transform.get(2));
            part.render(data, pose, output, light, entity);
        } finally {
            pose.popPose();
        }
    }
}
