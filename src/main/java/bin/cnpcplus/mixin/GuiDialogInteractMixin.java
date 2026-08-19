package bin.cnpcplus.mixin;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.util.FormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.player.GuiDialogInteract;
import noppes.npcs.controllers.data.DialogOption;
import noppes.npcs.shared.client.util.NoppesStringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiDialogInteract.class)
public class GuiDialogInteractMixin {

    @Inject(method = "drawString", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$drawDialogText(GuiGraphics graphics, String text, int left, int color, int count, CallbackInfo ci) {
        GuiDialogInteract self = (GuiDialogInteract)(Object)this;
        String fmt = CnpcPlusConfig.DIALOG_TEXT_FORMAT.get();
        int hex = FormatUtil.parseHexColor(CnpcPlusConfig.DIALOG_TEXT_COLOR.get(), 0xE0E0E0);
        String drawn = FormatUtil.applyDefault(text, fmt);
        int drawColor = FormatUtil.resolveColor(hex, fmt, color);
        if (FormatUtil.hasFormatCodes(text)) {
            drawColor = 0xFFFFFF;
        }

        int rowStart;
        try {
            var f = GuiDialogInteract.class.getDeclaredField("rowStart");
            f.setAccessible(true);
            rowStart = f.getInt(self);
        } catch (Exception e) {
            rowStart = 0;
        }

        int height = count - rowStart;
        int x = self.guiLeft + left;
        int y = self.guiTop + height * Minecraft.getInstance().font.lineHeight;
        graphics.drawString(Minecraft.getInstance().font, drawn, x, y, drawColor, false);
        ci.cancel();
    }

    @Redirect(method = "drawLinedOptions", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/util/NoppesStringUtils;formatText(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;"), remap = false)
    private String cnpcplus$optionFormat(String text, Object[] obs) {
        String fmt = CnpcPlusConfig.DIALOG_OPTION_FORMAT.get();
        String prepared = FormatUtil.applyDefault(text, fmt);
        return NoppesStringUtils.formatText(prepared.replace('\u00a7', '&'), obs);
    }

    @ModifyArg(method = "drawLinedOptions", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"), index = 4, remap = false)
    private int cnpcplus$optionColor(int color) {
        return color;
    }

    @Redirect(method = "drawWheel", at = @At(value = "FIELD", target = "Lnoppes/npcs/controllers/data/DialogOption;optionColor:I"), remap = false)
    private int cnpcplus$wheelOptionColor(DialogOption option) {
        return option.optionColor;
    }
}
