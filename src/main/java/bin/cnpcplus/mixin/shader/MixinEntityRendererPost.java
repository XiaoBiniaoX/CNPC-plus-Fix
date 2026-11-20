package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRendererPost {
    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/EntityRenderer;renderWorld(FJ)V", shift = At.Shift.AFTER))
    private void cnpcplus$postAfterWorld(float partialTicks, long nanoTime, CallbackInfo ci) {
        ClientShaders.renderPostAfterWorld();
    }
}
