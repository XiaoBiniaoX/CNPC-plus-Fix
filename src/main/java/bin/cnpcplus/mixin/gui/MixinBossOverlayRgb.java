package bin.cnpcplus.mixin.gui;

import bin.cnpcplus.bossbar.client.BossBarColors;
import net.minecraft.client.gui.GuiBossOverlay;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.world.BossInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Recolor the native resource-pack white bar; retain every native textured draw. */
@Mixin(GuiBossOverlay.class)
public abstract class MixinBossOverlayRgb {
    @Inject(method = "render", at = @At("HEAD"), require = 1)
    private void cnpcplus$tint(int x, int y, BossInfo info, CallbackInfo ci) {
        Integer rgb = BossBarColors.get(info.getUniqueId());
        if (rgb != null) GlStateManager.color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1);
    }
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BossInfo;getColor()Lnet/minecraft/world/BossInfo$Color;"), require = 2)
    private BossInfo.Color cnpcplus$texture(BossInfo info) {
        return BossBarColors.get(info.getUniqueId()) == null ? info.getColor() : BossInfo.Color.WHITE;
    }
    @Inject(method = "render", at = @At("RETURN"), require = 1)
    private void cnpcplus$restore(int x, int y, BossInfo info, CallbackInfo ci) { GlStateManager.color(1, 1, 1, 1); }
}
