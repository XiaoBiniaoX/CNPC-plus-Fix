package top.cnpcplus.mixin;

import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinShaderGameRenderer {
    // AFTER_LEVEL has finished, but renderHand has not yet cleared the world depth.
    // Use the actual world view pose, not Forge's AFTER_LEVEL event pose (which is projection).
    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z"), require = 1)
    private void cnpcplus$post(float partial, long time, PoseStack view, CallbackInfo ci) {
        top.cnpcplus.shader.client.ClientShaders.renderPost(partial, view.last().pose(), RenderSystem.getProjectionMatrix());
    }
}
