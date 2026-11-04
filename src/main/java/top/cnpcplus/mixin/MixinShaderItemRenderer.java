package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.shader.client.ClientShaders;

@Mixin(ItemRenderer.class)
public class MixinShaderItemRenderer {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, require = 1)
    private MultiBufferSource cnpcplus$buffer(MultiBufferSource source, ItemStack stack, ItemDisplayContext context, boolean left, PoseStack poses, MultiBufferSource original, int light, int overlay, BakedModel model) {
        return ClientShaders.wrapItem(stack, source);
    }
    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void cnpcplus$flush(ItemStack stack, ItemDisplayContext context, boolean left, PoseStack poses, MultiBufferSource source, int light, int overlay, BakedModel model, CallbackInfo ci) { ClientShaders.finish(source); }
}
