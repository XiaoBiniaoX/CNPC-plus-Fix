package top.cnpcplus.mixin;

import noppes.npcs.shared.client.gui.components.GuiBasicContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复物品栏页 guiTop 上移：imageHeight 200→224 导致 vanilla 居中逻辑把 guiTop 上移 12px。
 * 在 GuiBasicContainer.m_7856_ 计算完 guiTop 后立刻补偿，赶在 GuiContainerNPCInterface2
 * 的 menu.initGui（书签定位）之前 —— 否则书签仍按旧 guiTop 摆放，变成浮空书签。
 */
@Mixin(value = GuiBasicContainer.class, remap = false)
public class MixinGuiBasicContainerInvTopFix {

    @Inject(method = "m_7856_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/shared/client/gui/components/GuiWrapper;init(Lnet/minecraft/client/Minecraft;II)V",
            shift = At.Shift.BEFORE))
    private void cnpcplus$fixTopForEnlargedInv(CallbackInfo ci) {
        GuiBasicContainer<?> self = (GuiBasicContainer<?>) (Object) this;
        AbstractContainerScreenAccess access = (AbstractContainerScreenAccess) this;
        if (access.cnpcplus$getImageHeight() == 224) {
            self.guiTop += 12;
            access.cnpcplus$setTopPos(self.guiTop);
        }
    }
}
