package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import top.cnpcplus.shader.client.ClientShaders;

@Mixin(value = ItemRenderer.class, remap = false)
public class MixinShaderItemRenderer {
    @WrapMethod(method = "render", require = 1)
    private void cnpcplus$render(ItemStack stack, ItemDisplayContext context, boolean left, PoseStack poses, MultiBufferSource source, int light, int overlay, BakedModel model, Operation<Void> original) {
        MultiBufferSource wrapped = ClientShaders.wrapItem(stack, source);
        try { original.call(stack, context, left, poses, wrapped, light, overlay, model); }
        finally { if (wrapped != source) ClientShaders.finish(wrapped); }
    }
}
