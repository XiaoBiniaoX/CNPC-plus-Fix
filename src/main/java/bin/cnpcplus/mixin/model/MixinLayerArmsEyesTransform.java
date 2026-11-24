package bin.cnpcplus.mixin.model;

import bin.cnpcplus.model.client.PartTransforms;
import noppes.npcs.client.layer.LayerInterface;
import net.minecraft.client.renderer.entity.RenderLiving;
import noppes.npcs.client.layer.LayerArms;
import noppes.npcs.client.layer.LayerEyes;
import noppes.npcs.constants.EnumParts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {LayerArms.class, LayerEyes.class}, remap = false)
public abstract class MixinLayerArmsEyesTransform extends LayerInterface {
    protected MixinLayerArmsEyesTransform(RenderLiving render) { super(render); }
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelRenderer;postRender(F)V", shift = At.Shift.AFTER, remap = true), require = 1)
    private void cnpcplus$attached(float a, float b, float c, float d, float e, float f, CallbackInfo ci) {
        PartTransforms.apply(playerdata.getPartData((Object)this instanceof LayerArms ? EnumParts.CLAWS : EnumParts.EYES));
    }
}
