package bin.cnpcplus.mixin;

import net.minecraft.network.chat.Component;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityNPCInterface.class)
public class EntityNPCInterfaceMixin {

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$getName(CallbackInfoReturnable<Component> cir) {
        EntityNPCInterface self = (EntityNPCInterface)(Object)this;
        String raw = self.display.getName();
        if (raw.indexOf('&') >= 0) {
            cir.setReturnValue(Component.literal(raw.replace('&', '\u00a7')));
        }
    }
}
