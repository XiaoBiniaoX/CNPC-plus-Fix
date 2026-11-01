package top.cnpcplus.mixin;

import java.util.List;
import noppes.npcs.client.gui.script.GuiScriptInterface;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    @Inject(method = "m_7856_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$addSimpleCheck(CallbackInfo ci) {
        if (get(3) instanceof ScriptTextArea editor) {
            GuiButtonNop button = new GuiButtonNop(this, 421,
                    editor.x + editor.width - 100, editor.y + editor.height + 2,
                    100, 20, "cnpcplus.script.simple_check", pressed -> editor.simpleCheck());
            addButton(button);
        }
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
        return new ScriptTextArea(id, x, y, width, height, text,
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
