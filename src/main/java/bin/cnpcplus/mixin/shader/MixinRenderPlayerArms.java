package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayerArms {
    @Inject(method = {"renderRightArm", "renderLeftArm"}, at = @At("HEAD"))
    private void cnpcplus$target(AbstractClientPlayer player, CallbackInfo ci) { ClientShaders.pushTarget("entity:" + player.getUniqueID()); }
    @Inject(method = {"renderRightArm", "renderLeftArm"}, at = @At("RETURN"))
    private void cnpcplus$done(AbstractClientPlayer player, CallbackInfo ci) { ClientShaders.popTarget(); }
}
