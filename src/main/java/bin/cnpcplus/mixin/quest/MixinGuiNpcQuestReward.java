package bin.cnpcplus.mixin.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.global.GuiNpcQuestReward;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paint the whole 3x5 reward grid with the real slot texture
 * (customnpcs:textures/gui/slot.png, uv 0,0 18x18 - the same blit
 * GuiNPCInv uses for its slots), so all 15 cells share one texture
 * instead of a flat color patch on the extended area.
 */
@Mixin(value = GuiNpcQuestReward.class, remap = false)
public class MixinGuiNpcQuestReward {

    @Unique
    private static final ResourceLocation CNPCPLUS_SLOT_TEX =
            new ResourceLocation("customnpcs", "textures/gui/slot.png");

    @Inject(method = "func_146976_a", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/util/GuiContainerNPCInterface;func_146976_a(FII)V"), remap = false)
    private void cnpcplus$drawSlotBackground(float f, int i, int j, CallbackInfo ci) {
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        TextureManager tm = Minecraft.getMinecraft().renderEngine;
        tm.bindTexture(CNPCPLUS_SLOT_TEX);
        for (int l = 0; l < 3; ++l) {
            for (int k = 0; k < 5; ++k) {
                base.drawTexturedModalRect(base.field_147003_i + 72 + k * 18, base.field_147009_r + 17 + l * 18, 0, 0, 18, 18);
            }
        }
    }
}