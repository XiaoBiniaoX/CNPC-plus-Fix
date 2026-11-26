package bin.cnpcplus.script.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.shared.client.gui.components.*;
import noppes.npcs.shared.client.gui.listeners.*;
import top.cnpcplus.api.script.ScriptLanguageApi;

/** Detached selection. Browsing and Escape do not change page state. */
public final class SubGuiScriptLanguage extends GuiBasic implements ICustomScrollListener {
    private final List<String> values = new ArrayList<>(), labels = new ArrayList<>();
    private final Consumer<String> confirm;
    private int selected;

    public SubGuiScriptLanguage(Collection<String> nativeNames, String current, boolean page, Consumer<String> confirm) {
        this.confirm = confirm;
        drawDefaultBackground = false;
        var choices = new LinkedHashSet<String>();
        if (page) choices.add("");
        nativeNames.stream().sorted().map(ScriptLanguageApi::resolve).forEach(choices::add);
        ScriptLanguageApi.languages().forEach(language -> choices.add(language.id()));
        choices.add(ScriptLanguageApi.resolve(current));
        for (String value : choices) {
            values.add(value);
            String label = value.isEmpty() && page ? I18n.get("cnpcplus.script.language_inherit")
                    : ScriptLanguageApi.find(value).map(language -> ApiTextClient.resolve(language.displayName())).orElse(value);
            labels.add(label.isEmpty() ? I18n.get("cnpcplus.script.language_none") : label);
        }
        selected = values.indexOf(ScriptLanguageApi.resolve(current));
        if (selected < 0) selected = values.indexOf(current);
    }
    @Override public void init() {
        imageWidth = width * 9 / 10;
        imageHeight = height * 9 / 10;
        super.init();
        var list = new GuiCustomScrollNop(this, 0);
        list.disabledSearch();
        list.guiLeft = guiLeft + 10;
        list.guiTop = guiTop + 30;
        list.setSize(imageWidth - 20, imageHeight - 64);
        list.setUnsortedList(labels);
        list.setSelectedIndex(selected);
        list.scrollTo(labels.get(selected));
        addScroll(list);
        int buttonWidth = (imageWidth - 24) / 2;
        addButton(new GuiButtonNop(this, 1, guiLeft + 10, guiTop + imageHeight - 26, buttonWidth, 20, "gui.done", button -> {
            String value = values.get(selected); close(); confirm.accept(value);
        }));
        addButton(new GuiButtonNop(this, 2, guiLeft + 14 + buttonWidth, guiTop + imageHeight - 26,
                buttonWidth, 20, "gui.cancel", button -> close()));
    }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {
        int index = scroll.getSelectedIndex();
        if (index >= 0) selected = index;
    }
    @Override public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0, 0, width, height, 0x80000000);
        graphics.fill(guiLeft, guiTop, guiLeft + imageWidth, guiTop + imageHeight, 0xFF404040);
        graphics.fill(guiLeft + 1, guiTop + 1, guiLeft + imageWidth - 1, guiTop + imageHeight - 1, 0xFFC6C6C6);
        super.render(graphics, mouseX, mouseY, partial);
        graphics.drawString(font, I18n.get("cnpcplus.script.language_title"), guiLeft + 10, guiTop + 12, 0xFF202020, false);
    }
}
