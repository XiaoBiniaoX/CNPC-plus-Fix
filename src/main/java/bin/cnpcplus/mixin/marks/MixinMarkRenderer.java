package bin.cnpcplus.mixin.marks;

import bin.cnpcplus.marks.MarkRotation;
import bin.cnpcplus.marks.client.MarkTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.renderer.MarkRenderer;
import noppes.npcs.controllers.data.MarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MarkRenderer.class, remap = false)
public abstract class MixinMarkRenderer {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;func_179114_b(FFFF)V",
            shift = At.Shift.AFTER), require = 1)
    private static void cnpcplus$rotate(EntityLivingBase entity, double x, double y, double z, MarkData.Mark mark, CallbackInfo ci) {
        MarkRotation rotation = (MarkRotation) mark;
        float angle = (System.nanoTime() % 15_000_000_000L) / 1_000_000_000F * 24;
        if (rotation.cnpcplus$horizontal()) GlStateManager.rotate(angle, 0, 1, 0);
        if (rotation.cnpcplus$vertical()) GlStateManager.rotate(angle, 1, 0, 0);
        ResourceLocation texture = MarkTypes.texture(mark.type);
        if (texture != null) Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
    }
}
