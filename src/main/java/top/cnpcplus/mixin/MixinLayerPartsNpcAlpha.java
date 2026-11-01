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

@Mixin(value = LayerParts.class, remap = false)
public abstract class MixinLayerPartsNpcAlpha {
    @Redirect(method = "renderPart", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/parts/MpmPartAbstractClient;render(Lnoppes/npcs/client/parts/MpmPartData;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;)V"), require = 1)
    private static void cnpcplus$partAlpha(MpmPartAbstractClient part, MpmPartData data, PoseStack pose,
                                         MultiBufferSource buffer, int light, LivingEntity entity) {
        if (entity instanceof EntityNPCInterface npc && npc.display.getVisible() == 2) {
            part.render(data, pose, type -> new NpcAlphaVertexConsumer(buffer.getBuffer(type)), light, entity);
        } else {
            part.render(data, pose, buffer, light, entity);
        }
    }
}
