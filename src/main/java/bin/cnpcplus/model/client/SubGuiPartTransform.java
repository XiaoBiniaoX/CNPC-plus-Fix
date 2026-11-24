package bin.cnpcplus.model.client;

import bin.cnpcplus.model.PartTransform;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.util.*;

public final class SubGuiPartTransform extends SubGuiInterface implements ITextfieldListener {
    private final PartTransform transform;
    public SubGuiPartTransform(PartTransform transform) {
        this.transform = transform;
        xSize = 260; ySize = 182; closeOnEsc = true; title = "";
        setBackground("menubg.png");
    }
    @Override public void initGui() {
        super.initGui();
        addLabel(new GuiNpcLabel(10, "cnpcplus.part.scale", guiLeft + 8, guiTop + 10));
        addLabel(new GuiNpcLabel(11, "cnpcplus.part.offset", guiLeft + 8, guiTop + 74));
        for (int i = 0; i < 6; i++) {
            int x = guiLeft + 12 + i % 3 * 82, y = guiTop + 30 + i / 3 * 64;
            addLabel(new GuiNpcLabel(i, new String[]{"X", "Y", "Z"}[i % 3], x, y + 6));
            addTextField(new GuiNpcTextField(i, this, fontRenderer, x + 12, y, 64, 20, Float.toString(transform.get(i))));
        }
        addButton(new GuiNpcButton(10, guiLeft + 12, guiTop + 146, 110, 20, "gui.done"));
        addButton(new GuiNpcButton(11, guiLeft + 138, guiTop + 146, 110, 20, "gui.reset"));
    }
    @Override public void unFocused(GuiNpcTextField field) {
        try { transform.set(field.getId(), Float.parseFloat(field.getText().trim())); }
        catch (NumberFormatException invalid) { /* Keep the previous value for incomplete input. */ }
        field.setText(Float.toString(transform.get(field.getId())));
    }
    @Override public void save() { for (int i = 0; i < 6; i++) unFocused(getTextField(i)); }
    @Override public void buttonEvent(GuiButton button) {
        if (button.id == 10) close();
        if (button.id == 11) {
            for (int i = 0; i < 6; i++) transform.set(i, i < 3 ? 1 : 0);
            initGui();
        }
    }
}
