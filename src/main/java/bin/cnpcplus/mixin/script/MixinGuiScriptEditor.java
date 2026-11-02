package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.client.ScriptTextArea;
import bin.cnpcplus.script.client.SubGuiApiDocs;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.script.*;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptEditor extends GuiNPCInterface {
    @Shadow private int activeTab;
    @Shadow public IScriptHandler handler;
    @Shadow public List<String> methods;
    @Unique private int cnpcplus$panelWidth, cnpcplus$panelHeight;

    @Inject(method = "init()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/util/GuiNPCInterface;init()V"), require = 1)
    private void cnpcplus$fitPanel(CallbackInfo ci) {
        imageWidth = Math.min(imageWidth, width - 16);
        imageHeight = Math.min(height - 34, Math.max(220, imageHeight));
        cnpcplus$panelWidth = imageWidth; cnpcplus$panelHeight = imageHeight;
    }
    @Inject(method = "init()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$addControls(CallbackInfo ci) {
        imageWidth = cnpcplus$panelWidth; imageHeight = cnpcplus$panelHeight;
        if (get(3) instanceof ScriptTextArea editor) {
            int right = guiLeft + imageWidth - 6, left = editor.x + editor.width + 4;
            int sideWidth = right - left;
            for (int id : new int[]{99, 102, 101, 100, 105, 107}) {
                var side = getButton(id); if (side == null) continue;
                boolean second = id == 101 || id == 105;
                boolean half = id == 100 || id == 101 || id == 102 || id == 105;
                int halfWidth = (sideWidth - 2) / 2;
                side.setX(left + (second ? halfWidth + 2 : 0));
                side.setWidth(half ? (second ? sideWidth - halfWidth - 2 : halfWidth) : sideWidth);
            }
            int bottom = guiTop + imageHeight - 8;
            for (int id = 0; id <= 2; id++) {
                var scroll = getScroll(id); if (scroll == null) continue;
                scroll.guiLeft = left;
                if (id == 0) scroll.setSize(sideWidth, bottom - scroll.guiTop);
                else if (id == 1) scroll.setSize(sideWidth, (bottom - scroll.guiTop - 4) * 2 / 3);
                else {
                    var functions = getScroll(1);
                    scroll.guiTop = functions.guiTop + functions.getHeight() + 4;
                    scroll.setSize(sideWidth, bottom - scroll.guiTop);
                }
            }
            int buttonWidth = (editor.width - 4) / 2, y = editor.y + editor.height + 26;
            addButton(new GuiButtonNop(this, 422, editor.x, y, buttonWidth, 20, "cnpcplus.api.button",
                    pressed -> setSubGui(new SubGuiApiDocs(cnpcplus$context(), methods))));
            addButton(new GuiButtonNop(this, 421, editor.x + buttonWidth + 4, y,
                    editor.width - buttonWidth - 4, 20, "cnpcplus.script.simple_check", pressed -> editor.simpleCheck()));
        }
    }
    @Redirect(method = "init()V", at = @At(value = "NEW", target = "noppes/npcs/shared/client/gui/components/GuiTextArea"), require = 2)
    private GuiTextArea cnpcplus$scriptEditor(int id, int x, int y, int width, int height, String text) {
        if (activeTab < 1 || activeTab > handler.getScripts().size()) return new GuiTextArea(id, x, y, width, height, text);
        return new ScriptTextArea(id, x, y, width - 18, guiTop + cnpcplus$panelHeight - 6 - y, text,
                handler.getScripts().get(activeTab - 1), cnpcplus$context(), handler.getLanguage(), methods);
    }
    @Unique private String cnpcplus$context() {
        Object self = this;
        return self instanceof GuiScriptPlayers ? "PlayerEvent" : self instanceof GuiScriptDoor ? "BlockDoorEvent"
                : self instanceof GuiScriptBlock ? "BlockEvent" : self instanceof GuiScriptItem ? "ItemEvent"
                : self instanceof GuiScriptForge ? "ForgeEvent" : "NpcEvent";
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        if (drawDefaultBackground && !hasSubGui()) renderBackground(g, mx, my, partial);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth, guiTop + imageHeight, 0xFF555555);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth - 2, guiTop + imageHeight - 2, 0xFFFFFFFF);
        g.fill(guiLeft + 2, guiTop + 2, guiLeft + imageWidth - 2, guiTop + imageHeight - 2, 0xFFC6C6C6);
        var texture = background; boolean dim = drawDefaultBackground;
        background = null; drawDefaultBackground = false;
        super.render(g, mx, my, partial);
        background = texture; drawDefaultBackground = dim;
    }
    // GuiWrapper closes on Esc before component dispatch. Dismiss completion first.
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && !hasSubGui() && get(3) instanceof ScriptTextArea editor && editor.hasPopup())
            return editor.keyPressed(key, scan, modifiers);
        return super.keyPressed(key, scan, modifiers);
    }
}
