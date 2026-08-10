package bin.cnpcplus.mixin.puppet;

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

/**
 * ModelData hand scale - applied AFTER arm attach so scale is around item origin
 * (does not drag position via scaled arm offset).
 */
@Mixin(LayerHeldItem.class)
public class MixinLayerHeldItemScale {

    @Unique
    private final ThreadLocal cnpcplus$didPush = new ThreadLocal();

    @Inject(method = "renderHeldItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemSide(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            shift = At.Shift.BEFORE))
    private void cnpcplus$pre(EntityLivingBase entity, ItemStack stack, ItemCameraTransforms.TransformType type,
                              EnumHandSide hand, CallbackInfo ci) {
        cnpcplus$didPush.set(Boolean.FALSE);
        if (stack == null || stack.isEmpty()) return;
        float[] s = cnpcplus$scale(entity, hand);
        if (s == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.scale(s[0], s[1], s[2]);
        cnpcplus$didPush.set(Boolean.TRUE);
    }

    @Inject(method = "renderHeldItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemSide(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            shift = At.Shift.AFTER))
    private void cnpcplus$post(EntityLivingBase entity, ItemStack stack, ItemCameraTransforms.TransformType type,
                               EnumHandSide hand, CallbackInfo ci) {
        if (Boolean.TRUE.equals(cnpcplus$didPush.get())) {
            GlStateManager.popMatrix();
            cnpcplus$didPush.set(Boolean.FALSE);
        }
    }

    @Unique
    private static float[] cnpcplus$scale(EntityLivingBase entity, EnumHandSide hand) {
        try {
            Class<?> customNpc = Class.forName("noppes.npcs.entity.EntityCustomNpc");
            if (!customNpc.isInstance(entity)) return null;
            Object modelData = customNpc.getField("modelData").get(entity);
            if (modelData == null) return null;
            if (!Class.forName("bin.cnpcplus.accessor.EquipmentModelDataAccessor").isInstance(modelData)) return null;
            boolean main = hand == entity.getPrimaryHand();
            Object cfg = modelData.getClass().getMethod(main ? "getMainhand" : "getOffhand").invoke(modelData);
            if (cfg == null) return null;
            float sx = cfg.getClass().getField("scaleX").getFloat(cfg);
            float sy = cfg.getClass().getField("scaleY").getFloat(cfg);
            float sz = cfg.getClass().getField("scaleZ").getFloat(cfg);
            if (sx == 1.0f && sy == 1.0f && sz == 1.0f) return null;
            return new float[]{sx, sy, sz};
        } catch (Throwable t) {
            return null;
        }
    }
}
