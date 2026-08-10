package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.puppet.PuppetPartUtil;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerArmorBase.class)
public class MixinLayerArmorBasePuppetEquipment {

    @Unique
    private final ThreadLocal cnpcplus$pushed = new ThreadLocal();

    @Inject(method = "renderArmorLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V",
            shift = At.Shift.BEFORE))
    private void cnpcplus$pre(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale,
                              EntityEquipmentSlot slot, CallbackInfo ci) {
        cnpcplus$pushed.set(Boolean.FALSE);
        String getter;
        if (slot == EntityEquipmentSlot.HEAD) getter = "cnpcplus$getHelmet";
        else if (slot == EntityEquipmentSlot.CHEST) getter = "cnpcplus$getChestplate";
        else if (slot == EntityEquipmentSlot.LEGS) getter = "cnpcplus$getLeggings";
        else if (slot == EntityEquipmentSlot.FEET) getter = "cnpcplus$getBoots";
        else return;
        float[] t = PuppetPartUtil.partTransform(PuppetPartUtil.equipPart(entity, getter));
        if (t == null) return;
        GlStateManager.pushMatrix();
        float pivotY = slot == EntityEquipmentSlot.HEAD ? 1.5f
                : (slot == EntityEquipmentSlot.CHEST ? 1.0f : 0.5f);
        GlStateManager.translate(0.0f, pivotY, 0.0f);
        GlStateManager.translate(t[0], t[1], t[2]);
        GlStateManager.rotate(t[3], 1.0f, 0.0f, 0.0f);
        GlStateManager.rotate(t[4], 0.0f, 1.0f, 0.0f);
        GlStateManager.rotate(t[5], 0.0f, 0.0f, 1.0f);
        if (t[6] != 1.0f || t[7] != 1.0f || t[8] != 1.0f) {
            GlStateManager.scale(t[6], t[7], t[8]);
        }
        GlStateManager.translate(0.0f, -pivotY, 0.0f);
        cnpcplus$pushed.set(Boolean.TRUE);
    }

    @Inject(method = "renderArmorLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V",
            shift = At.Shift.AFTER))
    private void cnpcplus$post(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                               float ageInTicks, float netHeadYaw, float headPitch, float scale,
                               EntityEquipmentSlot slot, CallbackInfo ci) {
        if (Boolean.TRUE.equals(cnpcplus$pushed.get())) {
            GlStateManager.popMatrix();
            cnpcplus$pushed.set(Boolean.FALSE);
        }
    }
}

