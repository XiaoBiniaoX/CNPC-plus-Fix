package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.client.ScriptTextArea;
import bin.cnpcplus.script.client.SubGuiApiDocs;
import bin.cnpcplus.script.client.SubGuiScriptLanguage;
import bin.cnpcplus.script.client.ApiTextClient;
import top.cnpcplus.api.script.*;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.script.*;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Tooltip;

@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptEditor extends GuiNPCInterface {
    @Shadow private int activeTab;
    @Shadow public IScriptHandler handler;
    @Shadow public List<String> methods;
    @Shadow public Map<String, List<String>> languages;
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
            int buttonWidth = (editor.width - 8) / 3, y = editor.y + editor.height + 26;
            addButton(new GuiButtonNop(this, 422, editor.x, y, buttonWidth, 20, "cnpcplus.api.button",
                    pressed -> setSubGui(new SubGuiApiDocs(ScriptMetadataApi.legacyContext(cnpcplus$context()), methods))));
            addButton(new GuiButtonNop(this, 421, editor.x + buttonWidth + 4, y,
                    buttonWidth, 20, "cnpcplus.script.simple_check", pressed -> editor.simpleCheck()));
            var page = handler.getScripts().get(activeTab - 1);
            String effective = ScriptPageApi.getEffectiveLanguage(page);
            String label = ScriptLanguageApi.find(effective).map(value -> ApiTextClient.resolve(value.displayName())).orElse(effective);
            var languageButton = new GuiButtonNop(this, 423, editor.x + (buttonWidth + 4) * 2, y,
                    editor.width - (buttonWidth + 4) * 2, 20, "script.language", pressed -> cnpcplus$chooseLanguage(true));
            languageButton.setTooltip(Tooltip.create(Component.literal(label)));
            addButton(languageButton);
            var scroll = getScroll(1);
            if (scroll != null) {
                var hooks = ScriptMetadataApi.hooks(cnpcplus$context(), methods);
                scroll.listener = new ICustomScrollListener() {
                    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop list) {}
                    @Override public void scrollDoubleClicked(String selection, GuiCustomScrollNop list) {
                        hooks.stream().filter(hook -> hook.name().equals(selection)).findFirst().ifPresent(hook ->
                                editor.addText(ScriptEditorApi.hookTemplate(effective, hook)));
                    }
                };
            }
        } else if (activeTab == 0 && getButton(103) != null) {
            var old = getButton(103);
            String effective = ScriptLanguageApi.resolve(handler.getLanguage());
            String label = ScriptLanguageApi.find(effective).map(value -> ApiTextClient.resolve(value.displayName())).orElse(effective);
            var button = new GuiButtonNop(this, 103, old.getX(), old.getY(), old.getWidth(), old.getHeight(), "script.language",
                    pressed -> cnpcplus$chooseLanguage(false));
            button.setMessage(Component.literal(label));
            button.setTooltip(Tooltip.create(Component.literal(effective)));
            removeWidget(old);
            addButton(button);
        }
    }
    @Unique private void cnpcplus$chooseLanguage(boolean pageSelection) {
        var page = pageSelection ? handler.getScripts().get(activeTab - 1) : null;
        if (page != null && get(3) instanceof GuiTextArea editor) page.script = editor.getText();
        String current = page == null ? handler.getLanguage() : ScriptPageApi.getLanguageOverride(page);
        setSubGui(new SubGuiScriptLanguage(languages.keySet(), current, pageSelection, value -> {
            if (page == null) handler.setLanguage(value); else ScriptPageApi.setLanguageOverride(page, value);
            init();
        }));
    }
    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$languageButton(GuiButtonNop button, CallbackInfo ci) {
        if (button.id == 103) { cnpcplus$chooseLanguage(false); ci.cancel(); }
    }
    @Redirect(method = "buttonEvent", at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1)
    private Object cnpcplus$linkedFiles(Map<String, List<String>> choices, Object ignored) {
        var page = handler.getScripts().get(activeTab - 1);
        String effective = ScriptPageApi.getEffectiveLanguage(page);
        String extension = ScriptLanguageApi.extension(effective);
        if (extension.isEmpty()) return choices.getOrDefault(ScriptLanguageApi.engineName(effective), List.of());
        return choices.values().stream().flatMap(List::stream).filter(file -> file.toLowerCase(java.util.Locale.ROOT)
                .endsWith(extension.toLowerCase(java.util.Locale.ROOT))).distinct().sorted().toList();
    }
    @Redirect(method = "init()V", at = @At(value = "NEW", target = "noppes/npcs/shared/client/gui/components/GuiTextArea"), require = 2)
    private GuiTextArea cnpcplus$scriptEditor(int id, int x, int y, int width, int height, String text) {
        if (activeTab < 1 || activeTab > handler.getScripts().size()) return new GuiTextArea(id, x, y, width, height, text);
        return new ScriptTextArea(id, x, y, width - 18, guiTop + cnpcplus$panelHeight - 6 - y, text,
                handler.getScripts().get(activeTab - 1), cnpcplus$context(),
                ScriptPageApi.getEffectiveLanguage(handler.getScripts().get(activeTab - 1)), methods);
    }
    @Unique private String cnpcplus$context() {
        return ScriptMetadataApi.contextId(handler);
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
