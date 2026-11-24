package bin.cnpcplus.mixin.model;

import bin.cnpcplus.model.client.PartTransforms;
import noppes.npcs.ModelPartData;
import noppes.npcs.client.layer.LayerInterface;
import noppes.npcs.client.layer.LayerArms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LayerInterface.class, remap = false)
public abstract class MixinLayerPartTransform {
    @Inject(method = "preRender(Lnoppes/npcs/ModelPartData;)V", at = @At("RETURN"), require = 1)
    private void cnpcplus$transform(ModelPartData data, CallbackInfo ci) {
        if (!((Object)this instanceof LayerArms)) PartTransforms.apply(data);
    }
}
