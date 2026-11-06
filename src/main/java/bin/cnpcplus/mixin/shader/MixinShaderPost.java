package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Shader.class)
public abstract class MixinShaderPost {
    @Shadow @Final private ShaderManager manager;
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/shader/ShaderManager;useShader()V"))
    private void cnpcplus$parameters(float partial, CallbackInfo ci) { ClientShaders.bindPost(manager); }
}
