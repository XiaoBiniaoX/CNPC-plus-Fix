package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.puppet.PuppetPartUtil;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerHeldItem.class)
public class MixinLayerHeldItemPuppetEquipment {

    @Unique
    private final ThreadLocal cnpcplus$pushed = new ThreadLocal();

    @Inject(method = "renderHeldItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemSide(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            shift = At.Shift.BEFORE))
    private void cnpcplus$preItem(EntityLivingBase entity, ItemStack stack, ItemCameraTransforms.TransformType type,
                                  EnumHandSide hand, CallbackInfo ci) {
        cnpcplus$pushed.set(Boolean.FALSE);
        if (stack == null || stack.isEmpty()) return;
        boolean main = hand == entity.getPrimaryHand();
        Object part = PuppetPartUtil.equipPart(entity, main ? "cnpcplus$getMainhand" : "cnpcplus$getOffhand");
        float[] t = PuppetPartUtil.partTransform(part);
        if (t == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(t[0], t[1], t[2]);
        GlStateManager.rotate(t[3], 1.0f, 0.0f, 0.0f);
        GlStateManager.rotate(t[4], 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(t[5], 0.0f, 0.0f, 1.0f);
        if (t[6] != 1.0f || t[7] != 1.0f || t[8] != 1.0f) {
            GlStateManager.scale(t[6], t[7], t[8]);
        }
        cnpcplus$pushed.set(Boolean.TRUE);
    }

    @Inject(method = "renderHeldItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemSide(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            shift = At.Shift.AFTER))
    private void cnpcplus$postItem(EntityLivingBase entity, ItemStack stack, ItemCameraTransforms.TransformType type,
                                   EnumHandSide hand, CallbackInfo ci) {
        if (Boolean.TRUE.equals(cnpcplus$pushed.get())) {
            GlStateManager.popMatrix();
            cnpcplus$pushed.set(Boolean.FALSE);
        }
    }
}

