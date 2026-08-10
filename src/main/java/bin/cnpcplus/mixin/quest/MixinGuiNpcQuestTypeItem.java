package bin.cnpcplus.mixin.quest;

import bin.cnpcplus.mixin.AbstractContainerScreenAccess;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.questtypes.GuiNpcQuestTypeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcQuestTypeItem.class, remap = false)
public class MixinGuiNpcQuestTypeItem {

    @Unique
    private static final ResourceLocation SLOT_TEX =
            ResourceLocation.fromNamespaceAndPath("customnpcs", "textures/gui/slot.png");

    @Inject(method = "renderBg", at = @At("TAIL"))
    private void cnpcplus$drawSlotBg(GuiGraphics graphics, float partialTicks, int x, int y, CallbackInfo ci) {
        AbstractContainerScreenAccess acc = (AbstractContainerScreenAccess) this;
        int l = acc.cnpcplus$getLeftPos();
        int t = acc.cnpcplus$getTopPos();
        for (int r = 0; r < 3; ++r) {
            for (int k = 0; k < 3; ++k) {
                if (k == 1) continue;
                graphics.blit(SLOT_TEX, l + 19 + k * 25, t + 39 + r * 25, 0, 0, 18, 18);
            }
        }
    }
}
