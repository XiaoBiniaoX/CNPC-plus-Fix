package bin.cnpcplus.mixin.quest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.questtypes.GuiNpcQuestTypeItem;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paint the two extra slot columns (k=0 and k=2) of the 3x3 collect grid
 * with the real slot texture (customnpcs:textures/gui/slot.png, uv 0,0
 * 18x18 - the same blit GuiNPCInv uses for its slots), so the extension
 * looks identical to the vanilla middle column instead of a flat patch.
 */
@Mixin(value = GuiNpcQuestTypeItem.class, remap = false)
public class MixinGuiNpcQuestTypeItem {

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
            for (int k = 0; k < 3; ++k) {
                if (k == 1) continue;
                base.drawTexturedModalRect(base.field_147003_i + 19 + k * 25, base.field_147009_r + 39 + l * 25, 0, 0, 18, 18);
            }
        }
    }
}