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
 * 脚本控制台页在「网站/例子」上方加三个外链按钮。
 * 三按钮等宽拼出网站+例子总宽 161（53+1+53+1+53）。
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

        int left = website.getX();
        int y = website.getY();
        // 各自下移一行：109/112 原 y+78→y+100；110/111 原 y+99→y+121（勿统一设成同一 y）
        for (int id : new int[]{109, 112, 110, 111}) {
            GuiButtonNop b = self.getButton(id);
            if (b != null) ((AbstractWidgetAccess) (Object) b).cnpcplus$setY(b.getY() + 22);
        }

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
    }
}
