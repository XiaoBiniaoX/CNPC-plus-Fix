package top.cnpcplus.mixin;

import net.minecraft.Util;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.script.GuiScriptInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 控制台侧栏统一排列原按钮、外链和内置文档，避免多个TAIL注入相对挪动造成重叠。
 */
@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptInterfaceLinks {

    private static final String URL_PROMPT = "https://pan.baidu.com/s/16EhlHA6UXEK5qjEHDh7fxg?pwd=rhm8";
    private static final String URL_SKILLS = "https://github.com/SaltfishSheep/SKILL_CNPC_SCRIPTING";
    private static final String URL_BILI = "https://space.bilibili.com/353609880";

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addLinkButtons(CallbackInfo ci) {
        GuiScriptInterface self = (GuiScriptInterface) (Object) this;
        // 仅控制台页（无 activeTab 字段访问）：网站按钮存在才注入
        GuiButtonNop website = self.getButton(109);
        if (website == null || self.getButton(120) != null) return;

        int left = website.getX() - 17;
        int top = Math.min(self.guiTop + 10, self.height - 184);
        int y = top + 66;
        int w = 53;
        self.addButton(new GuiButtonNop(self, 120, left, y, w, 20, "cnpcplus.script.promptweb", button -> {
            self.setScreen(new ConfirmLinkScreen(ok -> {
                if (ok) Util.getPlatform().openUri(URL_PROMPT);
                self.setScreen(self);
            }, URL_PROMPT, true));
        }));
        self.getButton(120).setTooltip(Tooltip.create(Component.translatable("cnpcplus.script.promptweb.tooltip")));
        self.addButton(new GuiButtonNop(self, 121, left + 54, y, w, 20, "cnpcplus.script.aiskills", button -> {
            self.setScreen(new ConfirmLinkScreen(ok -> {
                if (ok) Util.getPlatform().openUri(URL_SKILLS);
                self.setScreen(self);
            }, URL_SKILLS, true));
        }));
        self.getButton(121).setTooltip(Tooltip.create(Component.translatable("cnpcplus.script.aiskills.tooltip")));
        self.addButton(new GuiButtonNop(self, 122, left + 108, y, w, 20, "cnpcplus.script.followus", button -> {
            self.setScreen(new ConfirmLinkScreen(ok -> {
                if (ok) Util.getPlatform().openUri(URL_BILI);
                self.setScreen(self);
            }, URL_BILI, true));
        }));
        self.getButton(122).setTooltip(Tooltip.create(Component.translatable("cnpcplus.script.followus.tooltip")));
        self.addButton(new GuiButtonNop(self, 422, left, top + 132, 161, 20, "cnpcplus.api.title",
                button -> self.setSubGui(new top.cnpcplus.script.client.SubGuiApiDocs())));
        int[][] positions = {{103, 60, 0, 101}, {104, 60, 22, 101}, {106, 0, 44, 161},
                {109, 0, 88, 80}, {112, 81, 88, 80}, {110, 0, 110, 80}, {111, 81, 110, 80},
                {100, 0, 154, 80}, {102, 81, 154, 80}};
        for (int[] p : positions) {
            GuiButtonNop button = self.getButton(p[0]);
            if (button == null) continue;
            button.setX(left + p[1]);
            button.setY(top + p[2]);
            button.setWidth(p[3]);
        }
        self.getLabel(1).setX(left);
        self.getLabel(1).setY(top + 5);
        self.getLabel(2).setX(left);
        self.getLabel(2).setY(top + 27);
        if (self.get(3) instanceof noppes.npcs.shared.client.gui.components.GuiTextArea console) {
            console.width = left - console.x - 6;
            var container = ((IGuiTextAreaScriptAccess)(Object)console).cnpcplus$container();
            container.lines.clear();
            container.init(IGuiTextAreaScriptAccess.cnpcplus$font(), console.width, console.height);
        }
    }
}
