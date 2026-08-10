package bin.cnpcplus.mixin.puppet;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ModelData equip scale for armor.
 * Applied just before model.render so it stacks with entity transform,
 * but we compensate by scaling around approximate body center (Y up half).
 * Combined with puppet mixin which may also push matrix.
 */
@Mixin(LayerArmorBase.class)
public class MixinLayerArmorBaseScale {

    @Unique
    private final ThreadLocal cnpcplus$didPush = new ThreadLocal();

    @Inject(method = "renderArmorLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V",
            shift = At.Shift.BEFORE))
    private void cnpcplus$pushScale(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                    float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                    EntityEquipmentSlot slot, CallbackInfo ci) {
        cnpcplus$didPush.set(Boolean.FALSE);
        float[] s = cnpcplus$scale(entity, slot);
        if (s == null) return;
        GlStateManager.pushMatrix();
        // Scale around body center-ish to reduce "scale moves position" for torso pieces
        // Head: pivot higher; legs: lower
        float pivotY = 0.0f;
        if (slot == EntityEquipmentSlot.HEAD) {
            pivotY = 1.5f;
        } else if (slot == EntityEquipmentSlot.CHEST) {
            pivotY = 1.0f;
        } else if (slot == EntityEquipmentSlot.LEGS || slot == EntityEquipmentSlot.FEET) {
            pivotY = 0.5f;
        }
        GlStateManager.translate(0.0f, pivotY, 0.0f);
        GlStateManager.scale(s[0], s[1], s[2]);
        GlStateManager.translate(0.0f, -pivotY, 0.0f);
        cnpcplus$didPush.set(Boolean.TRUE);
    }

    @Inject(method = "renderArmorLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V",
            shift = At.Shift.AFTER))
    private void cnpcplus$popScale(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                   float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                   EntityEquipmentSlot slot, CallbackInfo ci) {
        if (Boolean.TRUE.equals(cnpcplus$didPush.get())) {
            GlStateManager.popMatrix();
            cnpcplus$didPush.set(Boolean.FALSE);
        }
    }

    @Unique
    private static float[] cnpcplus$scale(EntityLivingBase entity, EntityEquipmentSlot slot) {
        try {
            Class<?> customNpc = Class.forName("noppes.npcs.entity.EntityCustomNpc");
            if (!customNpc.isInstance(entity)) return null;
            Object modelData = customNpc.getField("modelData").get(entity);
            if (modelData == null) return null;
            if (!Class.forName("bin.cnpcplus.accessor.EquipmentModelDataAccessor").isInstance(modelData)) return null;
            String g;
            if (slot == EntityEquipmentSlot.HEAD) g = "getHelmet";
            else if (slot == EntityEquipmentSlot.CHEST) g = "getChestplate";
            else if (slot == EntityEquipmentSlot.LEGS) g = "getLeggings";
            else if (slot == EntityEquipmentSlot.FEET) g = "getBoots";
            else return null;
            Object cfg = modelData.getClass().getMethod(g).invoke(modelData);
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
