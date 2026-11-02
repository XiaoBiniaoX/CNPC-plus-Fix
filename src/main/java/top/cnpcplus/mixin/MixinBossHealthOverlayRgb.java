package top.cnpcplus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.bossbar.client.BossBarColors;

/** Tint the resource-pack's native white sprite; vanilla owns layout, progress and notches. */
@Mixin(value = BossHealthOverlay.class, remap = false)
public abstract class MixinBossHealthOverlayRgb {
    @Redirect(method = "drawBar(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/BossEvent;I[Lnet/minecraft/resources/ResourceLocation;[Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V", ordinal = 0), require = 1)
    private void cnpcplus$tintSprite(GuiGraphics graphics, ResourceLocation sprite,
                                    int textureWidth, int textureHeight, int u, int v, int x, int y, int width, int height,
                                    GuiGraphics targetGraphics, int barX, int barY, BossEvent boss, int progress,
                                    ResourceLocation[] sprites, ResourceLocation[] overlays) {
        Integer rgb = BossBarColors.color(boss.getId());
        if (rgb == null) {
            graphics.blitSprite(sprite, textureWidth, textureHeight, u, v, x, y, width, height);
            return;
        }
        float[] color = RenderSystem.getShaderColor();
        float red = color[0], green = color[1], blue = color[2], alpha = color[3];
        graphics.setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha);
        try {
            graphics.blitSprite(sprites[BossEvent.BossBarColor.WHITE.ordinal()], textureWidth, textureHeight, u, v, x, y, width, height);
        } finally {
            graphics.setColor(red, green, blue, alpha);
        }
    }
}
