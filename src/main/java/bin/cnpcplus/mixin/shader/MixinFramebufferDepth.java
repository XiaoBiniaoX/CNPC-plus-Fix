package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Framebuffer.class)
public abstract class MixinFramebufferDepth {
    @ModifyArg(method = "framebufferClear", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;clear(I)V"), index = 0)
    private int cnpcplus$keepWorldDepth(int mask) { return ClientShaders.preserveMainDepth((Framebuffer)(Object)this) ? mask & ~256 : mask; }
}
