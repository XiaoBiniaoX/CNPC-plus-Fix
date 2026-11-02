package bin.cnpcplus.mixin.script;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiUtils;
import noppes.npcs.client.gui.script.GuiScriptInterface;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.net.URI;
import java.util.Collections;

@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptLinks extends GuiNPCInterface {
    @Unique private static final String[] CNPCPLUS_LINKS = {
            "https://pan.baidu.com/s/16EhlHA6UXEK5qjEHDh7fxg?pwd=rhm8",
            "https://github.com/SaltfishSheep/SKILL_CNPC_SCRIPTING",
            "https://space.bilibili.com/353609880"};
    @Unique private static final String[] CNPCPLUS_LABELS = {"cnpcplus.script.promptweb", "cnpcplus.script.aiskills", "cnpcplus.script.followus"};

    @Inject(method = "func_73866_w_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$addScriptLinks(CallbackInfo ci) {
        GuiNpcButton website = this.getButton(109);
        if (website == null) return;
        // Native init resets xSize/ySize to 420x256 at its tail. Derive both edges from the actual console.
        noppes.npcs.client.gui.util.GuiTextArea console = (noppes.npcs.client.gui.util.GuiTextArea) get(2);
        int x = console.x + console.width + 6, y = website.y;
        int sideWidth = this.width - guiLeft - 8 - x, halfWidth = (sideWidth - 2) / 2;
        getLabel(1).x = x; getLabel(2).x = x;
        for (int id : new int[]{103, 104}) {
            GuiNpcButton button = getButton(id); button.x = x + 60; button.width = sideWidth - 60;
        }
        GuiNpcButton folder = getButton(106);
        if (folder != null) { folder.x = x; folder.width = sideWidth; }
        for (int id : new int[]{100, 102}) { getButton(id).x = x; getButton(id).width = sideWidth; }
        for (int id : new int[]{109, 112, 110, 111}) {
            GuiNpcButton button = getButton(id);
            boolean second = id == 112 || id == 111;
            button.x = x + (second ? halfWidth + 2 : 0);
            button.width = second ? sideWidth - halfWidth - 2 : halfWidth;
        }
        for (int id : new int[]{109, 112, 110, 111, 100, 102}) {
            GuiNpcButton button = this.getButton(id);
            if (button != null) button.y += 22;
        }
        int linkWidth = (sideWidth - 4) / 3;
        for (int i = 0; i < 3; i++) this.addButton(new GuiNpcButton(120 + i, x + i * (linkWidth + 2), y,
                i == 2 ? sideWidth - 2 * (linkWidth + 2) : linkWidth, 20, CNPCPLUS_LABELS[i]));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$confirmScriptLink(GuiButton button, CallbackInfo ci) {
        if (button.id < 120 || button.id > 122) return;
        this.displayGuiScreen(new GuiConfirmOpenLink((GuiScriptInterface) (Object) this,
                CNPCPLUS_LINKS[button.id - 120], button.id, true));
        ci.cancel();
    }

    @Inject(method = "func_73878_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openScriptLink(boolean confirmed, int id, CallbackInfo ci) {
        if (id < 120 || id > 122) return;
        if (confirmed) {
            try { java.awt.Desktop.getDesktop().browse(URI.create(CNPCPLUS_LINKS[id - 120])); }
            catch (Exception error) { bin.cnpcplus.CnpcPlus.LOGGER.error("Cannot open scripting resource link", error); }
        }
        this.displayGuiScreen((GuiScriptInterface) (Object) this);
        ci.cancel();
    }

    @Override public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        if (drawDefaultBackground && !hasSubGui()) drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xFF555555);
        drawRect(guiLeft, guiTop, guiLeft + xSize - 2, guiTop + ySize - 2, 0xFFFFFFFF);
        drawRect(guiLeft + 2, guiTop + 2, guiLeft + xSize - 2, guiTop + ySize - 2, 0xFFC6C6C6);
        net.minecraft.util.ResourceLocation texture = background;
        boolean dim = drawDefaultBackground;
        background = null; drawDefaultBackground = false;
        super.func_73863_a(mouseX, mouseY, partialTicks);
        background = texture; drawDefaultBackground = dim;
        if (hasSubGui()) return;
        for (int i = 0; i < 3; i++) {
            GuiNpcButton button = this.getButton(120 + i);
            if (button != null && mouseX >= button.x && mouseX < button.x + button.width
                    && mouseY >= button.y && mouseY < button.y + button.height) {
                GuiUtils.drawHoveringText(Collections.singletonList(I18n.format(CNPCPLUS_LABELS[i] + ".tooltip")),
                        mouseX, mouseY, this.width, this.height, 240, this.getFontRenderer());
            }
        }
    }
}
