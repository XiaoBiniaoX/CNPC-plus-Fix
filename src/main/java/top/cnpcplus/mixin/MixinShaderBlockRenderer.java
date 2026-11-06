package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import top.cnpcplus.shader.client.ClientShaders;

@Mixin(value = BlockEntityRenderDispatcher.class, remap = false)
public class MixinShaderBlockRenderer {
    @Redirect(method = "setupAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"), require = 1)
    private static void cnpcplus$object(BlockEntityRenderer<BlockEntity> renderer, BlockEntity block, float partial, PoseStack poses, MultiBufferSource source, int light, int overlay) {
        String target = "block:" + block.getLevel().dimension().location() + ":" + block.getBlockPos().asLong();
        MultiBufferSource wrapped = ClientShaders.wrap(target, source, partial);
        try { renderer.render(block, partial, poses, wrapped, light, overlay); }
        finally { if (wrapped != source) ClientShaders.finish(wrapped); }
    }
}
