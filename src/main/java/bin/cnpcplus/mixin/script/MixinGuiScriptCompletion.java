package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.client.ScriptTextArea;
import noppes.npcs.client.gui.script.GuiScriptInterface;
import noppes.npcs.client.gui.util.GuiTextArea;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.IScriptHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptCompletion extends GuiNPCInterface {
    @Shadow private int activeTab;
    @Shadow public IScriptHandler handler;

    @Redirect(method = "func_73866_w_()V", at = @At(value = "NEW", target = "noppes/npcs/client/gui/util/GuiTextArea"), require = 2)
    private GuiTextArea cnpcplus$scriptEditor(int id, int x, int y, int width, int height, String text) {
        if (activeTab < 1 || activeTab > handler.getScripts().size()) return new GuiTextArea(id, x, y, width, height, text);
        String context = "NpcEvent", name = this.getClass().getSimpleName();
        if (name.contains("Player") || name.contains("Global")) context = "PlayerEvent";
        else if (name.contains("Forge")) context = "ForgeEvent";
        else if (name.contains("Block") || name.contains("Door")) context = "BlockEvent";
        else if (name.contains("Item")) context = "ItemEvent";
        return new ScriptTextArea(id, x, y, width, height, text, handler.getScripts().get(activeTab - 1), context, handler.getLanguage());
    }

    // The parent consumes Esc before IKeyListener gets it. Close the popup first, not the editor.
    @Override public void func_73869_a(char c, int key) {
        if (key == 1 && this.getSubGui() == null && this.get(2) instanceof ScriptTextArea
                && ((ScriptTextArea) this.get(2)).hasPopup()) {
            ((ScriptTextArea) this.get(2)).keyTyped(c, key); return;
        }
        super.func_73869_a(c, key);
    }
}
