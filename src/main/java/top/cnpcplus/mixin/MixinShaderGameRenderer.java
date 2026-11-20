package top.cnpcplus.mixin;

import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinShaderGameRenderer {
    @Unique
    private Matrix4f cnpcplus$view;
    @Unique
    private Matrix4f cnpcplus$projection;
    @Unique
    private float cnpcplus$partial;

    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void cnpcplus$beginFrame(float partial, long time, boolean renderLevel, CallbackInfo ci) {
        cnpcplus$view = null;
        cnpcplus$projection = null;
        top.cnpcplus.shader.client.ClientShaders.beginFrame();
    }

    // WORLD_COMPLETE: snapshot matrices and depth before the hand/vanilla post
    // passes clear world depth. This only reads the game target; no script runs here.
    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z"), require = 1)
    private void cnpcplus$post(float partial, long time, PoseStack view, CallbackInfo ci) {
        cnpcplus$partial = partial;
        cnpcplus$view = new Matrix4f(view.last().pose());
        cnpcplus$projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        top.cnpcplus.shader.client.ClientShaders.capturePostDepth();
    }

    // AFTER_GAME_POST / BEFORE_GUI: only the adapter sees the game framebuffer;
    // script PostChains run on their private target and then submit final color.
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V", shift = At.Shift.AFTER), require = 1)
    private void cnpcplus$processPost(float partial, long time, boolean renderLevel, CallbackInfo ci) {
        if (cnpcplus$view != null) {
            top.cnpcplus.shader.client.ClientShaders.renderPost(cnpcplus$partial, cnpcplus$view, cnpcplus$projection);
            cnpcplus$view = null;
            cnpcplus$projection = null;
        }
    }
}
