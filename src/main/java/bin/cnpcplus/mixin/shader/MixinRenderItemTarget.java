package bin.cnpcplus.mixin.shader;

import bin.cnpcplus.shader.client.ClientShaders;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public abstract class MixinRenderItemTarget {
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("HEAD"))
    private void cnpcplus$target(ItemStack stack, IBakedModel model, CallbackInfo ci) { ClientShaders.pushItem(stack); }
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V", at = @At("RETURN"))
    private void cnpcplus$done(ItemStack stack, IBakedModel model, CallbackInfo ci) { ClientShaders.popTarget(); }
}
