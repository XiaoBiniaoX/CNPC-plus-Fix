package top.cnpcplus.text.client;

import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.shared.client.gui.GuiTextAreaScreen;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import top.cnpcplus.mixin.GuiTextAreaScreenAccess;
import top.cnpcplus.text.StyledText;

/** Keep native selection, clipboard and close handling, adding only a style toolbar. */
public final class GuiStyledTextEditor extends GuiTextAreaScreen {
    private StyledText draft;
    private final Consumer<StyledText> commit;
    private final Function<StyledText, Screen> preview;
    private final StyledText original;
    private StyledTextArea area;
    private NativeTextToolbar toolbar;

    public GuiStyledTextEditor(StyledText text, Consumer<StyledText> commit, Function<StyledText, Screen> preview) {
        super(text.legacy().text());
        draft = text.legacy();
        original = draft;
        this.commit = commit;
        this.preview = preview;
    }

    @Override public void init() {
        super.init();
        GuiTextArea nativeArea = (GuiTextArea) get(2);
        draft = draft.edit(nativeArea.getText());
        int y = nativeArea.y;
        area = new StyledTextArea(2, nativeArea.x, y + 48, nativeArea.width,
                Math.max(20, nativeArea.height - 48), draft, () -> draft);
        area.setListener(this);
        wrapper.components.set(wrapper.components.indexOf(nativeArea), area);
        ((GuiTextAreaScreenAccess) (Object) this).cnpcplus$area(area);
        toolbar = new NativeTextToolbar(this, area, y, () -> draft, value -> draft = value, preview != null);
    }

    @Override public void textUpdate(String text) {
        if (draft != null) draft = draft.edit(text);
        super.textUpdate(text);
    }

    @Override public void buttonEvent(GuiButtonNop button) {
        if (button.id == 205) {
            draft = draft.edit(area.getText());
            Screen screen = preview == null ? null : preview.apply(draft);
            if (screen != null) setSubGui(screen);
        } else if (button.id == 103) {
            area.setText(original.text());
            draft = original;
        } else if (toolbar == null || !toolbar.click(button.id)) super.buttonEvent(button);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        super.render(graphics, mouseX, mouseY, partial);
        if (!hasSubGui() && toolbar != null) toolbar.draw(graphics);
    }

    @Override public void save() {
        if (area != null) { text = area.getText(); draft = draft.edit(text); }
        commit.accept(draft);
    }
}
