package bin.cnpcplus.mixin.puppet;

import bin.cnpcplus.puppet.PuppetPartUtil;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerCustomHead;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Head-slot non-armor (blocks etc): puppet helmet offset/rot + ModelData helmet scale.
 * After head postRender so transforms pivot on head.
 */
@Mixin(LayerCustomHead.class)
public class MixinLayerCustomHeadScale {

    @Unique
    private final ThreadLocal cnpcplus$pushed = new ThreadLocal();

    @Inject(method = "doRenderLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelRenderer;postRender(F)V",
            shift = At.Shift.AFTER))
    private void cnpcplus$afterHead(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                    float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        cnpcplus$pushed.set(Boolean.FALSE);
        float ox = 0, oy = 0, oz = 0, rx = 0, ry = 0, rz = 0, sx = 1, sy = 1, sz = 1;
        boolean any = false;

        float[] puppet = PuppetPartUtil.partTransform(PuppetPartUtil.equipPart(entity, "cnpcplus$getHelmet"));
        if (puppet != null) {
            ox = puppet[0];
            oy = puppet[1];
            oz = puppet[2];
            rx = puppet[3];
            ry = puppet[4];
            rz = puppet[5];
            sx *= puppet[6];
            sy *= puppet[7];
            sz *= puppet[8];
            any = true;
        }

        // ModelData helmet scale
        try {
            Class<?> customNpc = Class.forName("noppes.npcs.entity.EntityCustomNpc");
            if (customNpc.isInstance(entity)) {
                Object modelData = customNpc.getField("modelData").get(entity);
                if (modelData != null
                        && Class.forName("bin.cnpcplus.accessor.EquipmentModelDataAccessor").isInstance(modelData)) {
                    Object cfg = modelData.getClass().getMethod("getHelmet").invoke(modelData);
                    if (cfg != null) {
                        float msx = cfg.getClass().getField("scaleX").getFloat(cfg);
                        float msy = cfg.getClass().getField("scaleY").getFloat(cfg);
                        float msz = cfg.getClass().getField("scaleZ").getFloat(cfg);
                        if (msx != 1.0f || msy != 1.0f || msz != 1.0f) {
                            sx *= msx;
                            sy *= msy;
                            sz *= msz;
                            any = true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (!any) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(ox, oy, oz);
        if (rx != 0 || ry != 0 || rz != 0) {
            GlStateManager.rotate(rx, 1.0f, 0.0f, 0.0f);
            GlStateManager.rotate(ry, 0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(rz, 0.0f, 0.0f, 1.0f);
        }
        if (sx != 1.0f || sy != 1.0f || sz != 1.0f) {
            GlStateManager.scale(sx, sy, sz);
        }
        cnpcplus$pushed.set(Boolean.TRUE);
    }

    @Inject(method = "doRenderLayer", at = @At("RETURN"))
    private void cnpcplus$post(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                               float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        if (Boolean.TRUE.equals(cnpcplus$pushed.get())) {
            GlStateManager.popMatrix();
            cnpcplus$pushed.set(Boolean.FALSE);
        }
    }
}

