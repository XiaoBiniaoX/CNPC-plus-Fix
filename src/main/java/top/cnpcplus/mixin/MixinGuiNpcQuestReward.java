package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.global.GuiNpcQuestReward;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcQuestReward.class, remap = false)
public class MixinGuiNpcQuestReward {

    @Unique
    private static final ResourceLocation SLOT_TEX = new ResourceLocation("customnpcs", "textures/gui/slot.png");

    @Inject(method = "m_7286_", at = @At("TAIL"))
    private void cnpcplus$drawSlotBg(GuiGraphics graphics, float partialTicks, int x, int y, CallbackInfo ci) {
        AbstractContainerScreenAccess acc = (AbstractContainerScreenAccess) (Object) this;
        int l = acc.cnpcplus$getLeftPos();
        int t = acc.cnpcplus$getTopPos();
        for (int r = 0; r < 3; ++r) {
            for (int k = 0; k < 5; ++k) {
                graphics.blit(SLOT_TEX, l + 72 + k * 18, t + 17 + r * 18, 0, 0, 18, 18);
            }
        }
    }
}
