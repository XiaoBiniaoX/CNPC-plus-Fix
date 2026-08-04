package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiLabel.class)
public class MixinGuiLabelRecipeText {

    @Inject(method = "m_88315_", at = @At("HEAD"), remap = false)
    private void cnpcplus$replaceRecipeMatchText(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        GuiLabel self = (GuiLabel) (Object) this;
        String text = self.getMessage().getString();
        if ("gui.ignoreDamage".equals(text) || "忽略耐久值".equals(text) || "Ignore Damage".equals(text)) {
            self.setMessage(Component.literal("配置模糊化"));
        } else if ("gui.ignoreNBT".equals(text) || "忽略NBT值".equals(text) || "Ignore NBT".equals(text)) {
            self.setMessage(Component.literal("仅名字检查"));
        }
    }
}
