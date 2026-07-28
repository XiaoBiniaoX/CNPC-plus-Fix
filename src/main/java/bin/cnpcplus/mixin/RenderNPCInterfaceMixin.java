package bin.cnpcplus.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderNPCInterface.class)
public class RenderNPCInterfaceMixin {

    @Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"), remap = false)
    private MutableComponent cnpcplus$titleColor(String key) {
        if (key.indexOf('&') >= 0) {
            return Component.literal(key.replace('&', '\u00a7'));
        }
        return Component.translatable(key);
    }
}
