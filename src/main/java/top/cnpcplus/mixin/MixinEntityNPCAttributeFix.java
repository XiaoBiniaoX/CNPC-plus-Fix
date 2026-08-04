package top.cnpcplus.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCAttributeFix {

    @Inject(method = "registerBaseAttributes", at = @At("HEAD"), remap = false)
    private void fixMaxHealthAttribute(CallbackInfo ci) {
        ((MixinRangedAttributeAccessor)(Object)Attributes.MAX_HEALTH).setMaxValue(Double.MAX_VALUE);
    }
}
