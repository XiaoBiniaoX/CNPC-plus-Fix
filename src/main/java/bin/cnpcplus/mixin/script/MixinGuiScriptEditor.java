package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptError;
import bin.cnpcplus.script.ScriptErrorAccess;
import bin.cnpcplus.script.client.ApiCompletion;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.script.GuiScriptInterface;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.client.TextAreaSelectionAccess;

/** 保留原生布局/字体/保存链，补全浮层只在管理员主动编辑时显示。 */
@Mixin(value = GuiScriptInterface.class, remap = false)
public abstract class MixinGuiScriptEditor extends GuiNPCInterface {
    @Shadow(remap = false) private int activeTab;
    @Shadow(remap = false) public IScriptHandler handler;
    @Unique private ApiCompletion.Result cnpcplus$completion;
    @Unique private int cnpcplus$choice;
    @Unique private String cnpcplus$markedText;
    @Unique private GuiTextArea cnpcplus$markedArea;
    @Unique private ApiCompletion.CallTip cnpcplus$signature;
    @Unique private int cnpcplus$signatureCursor = -1;
    @Unique private String cnpcplus$signatureText;
    @Unique private ScriptError cnpcplus$markedError;

    @Inject(method = "init", at = @At("TAIL"), require = 1)
    private void cnpcplus$resetEditor(CallbackInfo ci) {
        cnpcplus$completion = null;
        cnpcplus$markedArea = null;
        cnpcplus$markedText = null;
        cnpcplus$signatureText = null;
    }

    @Unique private GuiTextArea cnpcplus$area() {
        return activeTab > 0 && !hasSubGui() && get(3) instanceof GuiTextArea area ? area : null;
    }

    @Unique private void cnpcplus$complete() {
        GuiTextArea area = cnpcplus$area();
        if (area == null || !area.active || !area.enabled || !(area instanceof TextAreaSelectionAccess access)) return;
        cnpcplus$completion = ApiCompletion.complete(area.text, access.cnpcplus$cursor(), cnpcplus$family());
        cnpcplus$choice = 0;
    }

    @Unique private String cnpcplus$family() {
        String name = getClass().getSimpleName();
        return name.contains("Player") ? "Player" : name.contains("Block") || name.contains("Door")
                ? "Block" : name.contains("Item") ? "Item" : name.contains("Forge") ? "Forge" : "Npc";
    }

    @Override public boolean charTyped(char c, int modifiers) {
        boolean consumed = super.charTyped(c, modifiers);
        if (c == '.' || cnpcplus$completion != null) cnpcplus$complete();
        return consumed;
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        GuiTextArea area = cnpcplus$area();
        if (area == null || !area.active || !area.enabled) {
            cnpcplus$completion = null;
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (key == 32 && Screen.hasControlDown()) { cnpcplus$complete(); return true; }
        if (cnpcplus$completion != null && !cnpcplus$completion.entries().isEmpty()) {
            int size = cnpcplus$completion.entries().size();
            if (key == 265 || key == 264) {
                cnpcplus$choice = Math.floorMod(cnpcplus$choice + (key == 265 ? -1 : 1), size);
                return true;
            }
            if (key == 256) { cnpcplus$completion = null; return true; }
            if ((key == 258 || key == 257) && !Screen.hasControlDown() && !Screen.hasShiftDown()) {
                ApiCompletion.Entry entry = cnpcplus$completion.entries().get(cnpcplus$choice);
                TextAreaSelectionAccess access = (TextAreaSelectionAccess) area;
                int start = cnpcplus$completion.start(), end = cnpcplus$completion.end();
                boolean parentheses = entry.method() && (end >= area.text.length() || area.text.charAt(end) != '(');
                String insertion = entry.name() + (parentheses ? "()" : "");
                access.cnpcplus$select(start, end, end);
                area.addText(insertion);
                int cursor = start + insertion.length() - (parentheses && entry.parameters() > 0 ? 1 : 0);
                access.cnpcplus$select(cursor, cursor, cursor);
                cnpcplus$completion = null;
                return true;
            }
        }
        boolean consumed = super.keyPressed(key, scanCode, modifiers);
        if (key == 259 && cnpcplus$completion != null) cnpcplus$complete();
        else cnpcplus$completion = null;
        return consumed;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        cnpcplus$completion = null;
        return super.mouseClicked(x, y, button);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTextArea area = cnpcplus$area();
        ScriptError error = null;
        if (area != null && handler != null && activeTab <= handler.getScripts().size()) {
            ScriptContainer container = handler.getScripts().get(activeTab - 1);
            if (container instanceof ScriptErrorAccess access) error = access.cnpcplus$error();
            if (area != cnpcplus$markedArea || !area.text.equals(cnpcplus$markedText)
                    || !java.util.Objects.equals(error, cnpcplus$markedError)) {
                int[] range = error == null ? null : error.range(area.text);
                if (area instanceof TextAreaSelectionAccess access) {
                    access.cnpcplus$highlightError(range == null ? -1 : range[0], range == null ? -1 : range[1]);
                }
                cnpcplus$markedArea = area;
                cnpcplus$markedText = area.text;
                cnpcplus$markedError = error;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (area == null) return;
        if (area instanceof TextAreaSelectionAccess access && area.active && area.enabled) {
            int cursor = access.cnpcplus$cursor();
            if (!area.text.equals(cnpcplus$signatureText) || cursor != cnpcplus$signatureCursor) {
                cnpcplus$signature = ApiCompletion.signature(area.text, cursor, cnpcplus$family());
                cnpcplus$signatureText = area.text;
                cnpcplus$signatureCursor = cursor;
            }
        } else cnpcplus$signature = null;
        int y = Math.min(height - 10, area.y + area.height + 2);
        Component status = Component.translatable("cnpcplus.script.editor.help");
        if (error != null) status = Component.translatable(error.matches(area.text)
                ? "cnpcplus.script.editor.error" : "cnpcplus.script.editor.stale", error.line());
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), area.width), area.x, y,
                error == null ? 0xA0A0A0 : 0xFF7777, false);
        if (error != null && mouseY >= y && mouseY <= y + 9 && mouseX >= area.x && mouseX <= area.x + area.width) {
            graphics.renderTooltip(font, Component.literal(error.message()), mouseX, mouseY);
        }
        if (cnpcplus$completion == null || cnpcplus$completion.entries().isEmpty() || !area.active) {
            if (cnpcplus$signature != null) {
                String signature = cnpcplus$signature.entries().getFirst().signature();
                int top = area.y + area.height - 13;
                graphics.fill(area.x, top, area.x + area.width, top + 13, 0xF0202020);
                graphics.drawString(font, font.plainSubstrByWidth(signature, area.width - 8), area.x + 4,
                        top + 2, 0x9BCCED, false);
                if (mouseX >= area.x && mouseX <= area.x + area.width && mouseY >= top && mouseY <= top + 13) {
                    graphics.renderTooltip(font, Component.literal(String.join("\n", cnpcplus$signature.entries()
                            .stream().map(ApiCompletion.Entry::signature).toList())), mouseX, mouseY);
                }
            }
            return;
        }
        int visible = Math.min(6, cnpcplus$completion.entries().size());
        int from = Math.max(0, Math.min(cnpcplus$choice - visible + 1, cnpcplus$completion.entries().size() - visible));
        int w = Math.min(area.width, 340), x = area.x + area.width - w;
        int top = Math.max(area.y, area.y + area.height - visible * 12 - 14);
        graphics.fill(x, top, x + w, top + visible * 12 + 13, 0xF0202020);
        graphics.fill(x, top, x + 2, top + visible * 12 + 13, 0xFF6BA9CF);
        for (int i = 0; i < visible; i++) {
            int index = from + i, row = top + i * 12;
            if (index == cnpcplus$choice) graphics.fill(x + 2, row, x + w, row + 12, 0xFF344D60);
            String signature = cnpcplus$completion.entries().get(index).signature();
            graphics.drawString(font, font.plainSubstrByWidth(signature, w - 8), x + 5, row + 2, 0xEEEEEE, false);
        }
        graphics.drawString(font, font.plainSubstrByWidth(Component.translatable("cnpcplus.script.editor.accept").getString(), w - 8),
                x + 5, top + visible * 12 + 2, 0xA0A0A0, false);
        if (mouseX >= x && mouseX <= x + w && mouseY >= top && mouseY < top + visible * 12) {
            int index = from + (mouseY - top) / 12;
            graphics.renderTooltip(font, Component.literal(cnpcplus$completion.entries().get(index).signature()), mouseX, mouseY);
        }
    }
}
