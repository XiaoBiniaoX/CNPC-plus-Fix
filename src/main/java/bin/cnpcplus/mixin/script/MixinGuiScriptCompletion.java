package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.client.ScriptTextArea;
import bin.cnpcplus.script.client.SubGuiApiDocs;
import noppes.npcs.client.gui.script.GuiScriptInterface;
import noppes.npcs.client.gui.util.GuiTextArea;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.controllers.IScriptHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.lwjgl.input.Keyboard;

@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptCompletion extends GuiNPCInterface {
    @Shadow private int activeTab;
    @Shadow public IScriptHandler handler;
    @Unique private Boolean cnpcplus$previousKeyRepeat;
    @Unique private int cnpcplus$panelWidth, cnpcplus$panelHeight, cnpcplus$editorTab;
    @Unique private ScriptTextArea cnpcplus$editor;
    @Unique private String cnpcplus$editorLanguage;

    @Inject(method = "func_73866_w_()V", at = @At("HEAD"), require = 1)
    private void cnpcplus$enableKeyRepeat(CallbackInfo ci) {
        if (cnpcplus$previousKeyRepeat == null) cnpcplus$previousKeyRepeat = Keyboard.areRepeatEventsEnabled();
        Keyboard.enableRepeatEvents(true);
        if (get(2) instanceof ScriptTextArea) cnpcplus$editor = (ScriptTextArea) get(2);
    }

    @Inject(method = "func_73866_w_()V", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/util/GuiNPCInterface;func_73866_w_()V"), require = 1)
    private void cnpcplus$fitPanel(CallbackInfo ci) {
        xSize = Math.min(xSize, width - 16);
        ySize = Math.min(height - 34, Math.max(220, ySize));
        cnpcplus$panelWidth = xSize; cnpcplus$panelHeight = ySize;
    }

    @Override public void func_146281_b() {
        if (cnpcplus$previousKeyRepeat != null) {
            Keyboard.enableRepeatEvents(cnpcplus$previousKeyRepeat);
            cnpcplus$previousKeyRepeat = null;
        }
        super.func_146281_b();
    }

    @Redirect(method = "func_73866_w_()V", at = @At(value = "NEW", target = "noppes/npcs/client/gui/util/GuiTextArea"), require = 2)
    private GuiTextArea cnpcplus$scriptEditor(int id, int x, int y, int width, int height, String text) {
        if (activeTab < 1 || activeTab > handler.getScripts().size()) return new GuiTextArea(id, x, y, width, height, text);
        String context = "NpcEvent", name = this.getClass().getSimpleName();
        if (name.contains("Player") || name.contains("Global")) context = "PlayerEvent";
        else if (name.contains("Forge")) context = "ForgeEvent";
        else if (name.contains("Door")) context = "BlockDoorEvent";
        else if (name.contains("Block")) context = "BlockEvent";
        else if (name.contains("Item")) context = "ItemEvent";
        int availableHeight = guiTop + cnpcplus$panelHeight - 6 - y - 24;
        if (cnpcplus$editor != null && cnpcplus$editorTab == activeTab && cnpcplus$editor.getText().equals(text)
                && java.util.Objects.equals(cnpcplus$editorLanguage, handler.getLanguage())) {
            cnpcplus$editor.resize(x, y, width - 18, availableHeight); return cnpcplus$editor;
        }
        cnpcplus$editorTab = activeTab;
        cnpcplus$editorLanguage = handler.getLanguage();
        return cnpcplus$editor = new ScriptTextArea(id, x, y, width - 18, availableHeight, text,
                handler.getScripts().get(activeTab - 1), context, handler.getLanguage());
    }

    @Inject(method = "func_73866_w_()V", at = @At("RETURN"), require = 1)
    private void cnpcplus$checkButton(CallbackInfo ci) {
        xSize = cnpcplus$panelWidth; ySize = cnpcplus$panelHeight;
        if (!(get(2) instanceof ScriptTextArea)) return;
        ScriptTextArea editor = (ScriptTextArea) get(2);
        int left = editor.x + editor.width + 4, sideWidth = guiLeft + xSize - 6 - left;
        for (int id : new int[]{102, 101, 100, 105, 107}) {
            GuiNpcButton side = getButton(id);
            boolean second = id == 101 || id == 105, half = id != 107;
            int halfWidth = (sideWidth - 2) / 2;
            side.x = left + (second ? halfWidth + 2 : 0);
            side.width = half ? second ? sideWidth - halfWidth - 2 : halfWidth : sideWidth;
        }
        noppes.npcs.client.gui.util.GuiCustomScroll scroll = getScroll(0);
        scroll.guiLeft = left; scroll.setSize(sideWidth, guiTop + ySize - 8 - scroll.guiTop);
        int buttonWidth = (editor.width - 4) / 2, y = editor.y + editor.height + 26;
        addButton(new GuiNpcButton(422, editor.x, y, buttonWidth, 20, "cnpcplus.api.button"));
        addButton(new GuiNpcButton(421, editor.x + buttonWidth + 4, y, editor.width - buttonWidth - 4, 20, "cnpcplus.script.simple_check"));
    }
    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$check(GuiButton button, CallbackInfo ci) {
        if (hasSubGui()) { getSubGui().buttonEvent(button); ci.cancel(); return; }
        if (button.id == 422 && get(2) instanceof ScriptTextArea) {
            mc.displayGuiScreen(new SubGuiApiDocs((GuiScriptInterface) (Object) this)); ci.cancel();
        }
        if (button.id == 421 && get(2) instanceof ScriptTextArea) {
            ((ScriptTextArea) get(2)).simpleCheck(); ci.cancel();
        }
        if ((button.id == 101 || button.id == 102) && get(2) instanceof ScriptTextArea) {
            // Whole-document actions keep the same undo history; reset the caret after native setText.
            ((ScriptTextArea) get(2)).setText(button.id == 102 ? "" : noppes.npcs.NoppesStringUtils.getClipboardContents());
            IGuiTextAreaScriptAccess area = (IGuiTextAreaScriptAccess) get(2);
            area.cnpcplus$cursorPosition(0); area.cnpcplus$selectionStart(0); area.cnpcplus$selectionEnd(0);
            ((ScriptTextArea) get(2)).clicked = false;
            ((ScriptTextArea) get(2)).active = true;
            ci.cancel();
        }
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
