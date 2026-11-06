package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public abstract class MixinShaderDraw {
    @Inject(method = {"glDrawArrays", "callList", "glBegin"}, at = @At("HEAD"))
    private static void cnpcplus$bind(CallbackInfo ci) { ClientShaders.beginDraw(); }
    @Inject(method = {"glDrawArrays", "callList", "glEnd"}, at = @At("RETURN"))
    private static void cnpcplus$restore(CallbackInfo ci) { ClientShaders.endDraw(); }
    @Inject(method = "glNewList", at = @At("HEAD"))
    private static void cnpcplus$compile(CallbackInfo ci) { ClientShaders.compilingList = true; }
    @Inject(method = "glEndList", at = @At("RETURN"))
    private static void cnpcplus$compiled(CallbackInfo ci) { ClientShaders.compilingList = false; }
}
