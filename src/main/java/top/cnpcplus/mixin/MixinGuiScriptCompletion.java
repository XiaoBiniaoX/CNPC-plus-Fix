package top.cnpcplus.mixin;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.script.GuiScriptInterface;
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
import top.cnpcplus.script.client.ScriptTextArea;

@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptCompletion extends GuiNPCInterface {
    @Shadow private int activeTab;
    @Shadow public IScriptHandler handler;
    @Shadow public List<String> methods;
    @Unique private int cnpcplus$panelWidth, cnpcplus$panelHeight;

    // Set the real panel dimensions before native controls are built; the native
    // tail resets imageWidth/Height for its scaled texture, not for layout.
    @Inject(method = "m_7856_()V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/util/GuiNPCInterface;m_7856_()V"), require = 1)
    private void cnpcplus$fitPanel(CallbackInfo ci) {
        imageWidth = Math.min(imageWidth, width - 16);
        imageHeight = Math.min(height - 34, Math.max(190, imageHeight));
        cnpcplus$panelWidth = imageWidth;
        cnpcplus$panelHeight = imageHeight;
    }

    @Inject(method = "m_7856_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$addSimpleCheck(CallbackInfo ci) {
        imageWidth = cnpcplus$panelWidth;
        imageHeight = cnpcplus$panelHeight;
        if (get(3) instanceof ScriptTextArea editor) {
            int right = guiLeft + imageWidth - 6;
            int left = editor.x + editor.width + 4;
            int sideWidth = right - left;
            for (int id : new int[]{99, 102, 101, 100, 105, 107}) {
                var side = getButton(id);
                if (side == null) continue;
                boolean second = id == 101 || id == 105;
                boolean half = id == 100 || id == 101 || id == 102 || id == 105;
                int halfWidth = (sideWidth - 2) / 2;
                side.setX(left + (second ? halfWidth + 2 : 0));
                side.setWidth(half ? (second ? sideWidth - halfWidth - 2 : halfWidth) : sideWidth);
            }
            int bottom = guiTop + imageHeight - 8;
            for (int id = 0; id <= 2; id++) {
                var scroll = getScroll(id);
                if (scroll == null) continue;
                scroll.guiLeft = left;
                if (id == 0) scroll.setSize(sideWidth, bottom - scroll.guiTop);
                else if (id == 1) scroll.setSize(sideWidth, (bottom - scroll.guiTop - 4) * 2 / 3);
                else {
                    var functions = getScroll(1);
                    scroll.guiTop = functions.guiTop + functions.getHeight() + 4;
                    scroll.setSize(sideWidth, bottom - scroll.guiTop);
                }
            }
            int buttonWidth = (editor.width - 4) / 2;
            int y = editor.y + editor.height + 26;
            addButton(new GuiButtonNop(this, 422, editor.x, y, buttonWidth, 20, "cnpcplus.api.button",
                    pressed -> setSubGui(new top.cnpcplus.script.client.SubGuiApiDocs())));
            GuiButtonNop button = new GuiButtonNop(this, 421,
                    editor.x + buttonWidth + 4, y,
                    editor.width - buttonWidth - 4, 20, "cnpcplus.script.simple_check", pressed -> editor.simpleCheck());
            addButton(button);
        }
    }

    @Override
    public void m_88315_(GuiGraphics g, int mx, int my, float partial) {
        if (drawDefaultBackground && !hasSubGui()) renderBackground(g);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth, guiTop + imageHeight, 0xFF555555);
        g.fill(guiLeft, guiTop, guiLeft + imageWidth - 2, guiTop + imageHeight - 2, 0xFFFFFFFF);
        g.fill(guiLeft + 2, guiTop + 2, guiLeft + imageWidth - 2, guiTop + imageHeight - 2, 0xFFC6C6C6);
        var texture = background;
        boolean dim = drawDefaultBackground;
        background = null;
        drawDefaultBackground = false;
        super.m_88315_(g, mx, my, partial);
        background = texture;
        drawDefaultBackground = dim;
    }

    @Redirect(
            method = "m_7856_()V",
            at = @At(value = "NEW", target = "noppes/npcs/shared/client/gui/components/GuiTextArea"),
            require = 2
    )
    private GuiTextArea cnpcplus$scriptEditor(int id, int x, int y, int width, int height, String text) {
        if (this.activeTab < 1 || this.activeTab > this.handler.getScripts().size()) {
            return new GuiTextArea(id, x, y, width, height, text);
        }
        String context = "NpcEvent";
        String name = getClass().getSimpleName();
        if (name.contains("Player") || name.contains("Global")) context = "PlayerEvent";
        else if (name.contains("Forge")) context = "ForgeEvent";
        else if (name.contains("Door")) context = "BlockDoorEvent";
        else if (name.contains("Block")) context = "BlockEvent";
        else if (name.contains("Item")) context = "ItemEvent";
        return new ScriptTextArea(id, x, y, width - 18, height, text,
                this.handler.getScripts().get(this.activeTab - 1), context, this.handler.getLanguage(), this.methods);
    }

    // GuiWrapper closes the screen on Esc before dispatching to components; dismiss completion first.
    @Override
    public boolean m_7933_(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && !hasSubGui() && get(3) instanceof ScriptTextArea editor
                && editor.hasPopup()) {
            return editor.m_7933_(keyCode, scanCode, modifiers);
        }
        return super.m_7933_(keyCode, scanCode, modifiers);
    }
}
