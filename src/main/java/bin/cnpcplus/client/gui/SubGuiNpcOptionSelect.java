package bin.cnpcplus.client.gui;

import bin.cnpcplus.mixin.advanced.IGuiButtonOptions;
import bin.cnpcplus.transport.client.Pinyin;
import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.entity.EntityNPCInterface;
import bin.cnpcplus.text.client.PlayerTextPreview;
import org.lwjgl.opengl.GL11;

/** Detached native-option picker: browsing only previews; Done applies through the original handler. */
public final class SubGuiNpcOptionSelect extends GuiQuestPanel implements ICustomScrollListener, ISliderListener {
    private final GuiNpcButton original;
    private final String[] options;
    private final String titleKey;
    private final int[] animations;
    private final IntConsumer accept;
    private final EntityNPCInterface preview;
    private final List<Integer> visible = new ArrayList<>();
    private GuiCustomScroll list;
    private int pending;
    private int listWidth;
    private int previewRotation = 150;
    private String search = "";

    public SubGuiNpcOptionSelect(GuiNpcButton original, String titleKey, int[] animations, IntConsumer accept) {
        this(original, titleKey, null, animations, accept);
    }

    public SubGuiNpcOptionSelect(GuiNpcButton original, String titleKey, EntityNPCInterface npc,
            int[] animations, IntConsumer accept) {
        this.original = original;
        this.options = ((IGuiButtonOptions) original).cnpcplus$options().clone();
        this.titleKey = titleKey;
        this.animations = animations;
        this.accept = accept;
        this.pending = original.getValue();
        preview = animations != null && npc != null ? PlayerTextPreview.npc(npc) : null;
        this.closeOnEsc = true;
    }

    @Override public void initGui() {
        xSize = Math.min(390, width - 12);
        ySize = Math.min(236, height - 12);
        super.initGui();
        listWidth = (xSize - 32) / 2;
        addTextField(new DragSearchField(0, this, guiLeft + 12, guiTop + 32, listWidth, 18, search));
        list = new GuiCustomScroll(this, 0);
        list.setSize(listWidth, ySize - 94);
        list.guiLeft = guiLeft + 12;
        list.guiTop = guiTop + 56;
        addScroll(list);
        if (animations != null) {
            addSlider(new GuiNpcSlider(this, 2, guiLeft + listWidth + 22, guiTop + ySize - 60,
                    xSize - listWidth - 34, 20, previewRotation / 360F));
        }
        addButton(new GuiNpcButton(0, guiLeft + xSize - 152, guiTop + ySize - 28, 68, 20, "gui.done"));
        addButton(new GuiNpcButton(1, guiLeft + xSize - 80, guiTop + ySize - 28, 68, 20, "gui.cancel"));
        filter();
    }

    private void filter() {
        visible.clear();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < options.length; i++) {
            String label = optionLabel(i);
            if (Pinyin.matches(search, label) || Pinyin.matches(search, options[i])) {
                visible.add(i);
                labels.add(label);
            }
        }
        list.setUnsortedList(labels);
        list.selected = visible.indexOf(pending);
        getButton(0).enabled = list.selected >= 0;
    }

    private String optionLabel(int index) {
        return I18n.translateToLocal(options[index]);
    }

    private void choose() {
        if (list.selected < 0 || list.selected >= visible.size()) return;
        pending = visible.get(list.selected);
    }

    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll scroll) {
        choose(); getButton(0).enabled = scroll.selected >= 0;
    }
    @Override public void scrollDoubleClicked(String label, GuiCustomScroll scroll) { choose(); }

    @Override public void keyTyped(char character, int key) throws java.io.IOException {
        super.keyTyped(character, key);
        String value = getTextField(0).getText();
        if (!value.equals(search)) { search = value; filter(); }
    }

    @Override public void buttonEvent(GuiButton button) {
        if (button.id == 0 && list.selected >= 0 && list.selected < visible.size()) {
            pending = visible.get(list.selected);
            close();
            accept.accept(pending);
        } else if (button.id == 1) close();
    }

    @Override public void mouseDragged(GuiNpcSlider slider) {
        previewRotation = Math.round(slider.sliderValue * 360);
        slider.setString(net.minecraft.client.resources.I18n.format("cnpcplus.select.rotation", previewRotation));
    }
    @Override public void mousePressed(GuiNpcSlider slider) {}
    @Override public void mouseReleased(GuiNpcSlider slider) {}

    @Override protected void drawPanelContents(int mouseX, int mouseY, float partialTicks) {
        fontRenderer.drawString(I18n.translateToLocal(titleKey), guiLeft + 12, guiTop + 10, 0x202020);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(net.minecraft.client.resources.I18n.format(
                "cnpcplus.select.current", optionLabel(original.getValue())), xSize - 24), guiLeft + 12, guiTop + 22, 0x686868);
        int panelX = guiLeft + listWidth + 22;
        int panelY = guiTop + 32;
        int panelWidth = xSize - listWidth - 34;
        int panelHeight = ySize - (animations == null ? 70 : 98);
        drawRect(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, 0xFFA49D89);
        drawRect(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF343B40);
        drawCenteredString(fontRenderer, optionLabel(pending), panelX + panelWidth / 2, panelY + 8, 0xFFEEE8D4);
        if (animations != null && preview != null && pending < animations.length) {
            preview.currentAnimation = animations[pending];
            preview.ais.animationType = animations[pending];
            drawRect(panelX + 12, panelY + panelHeight - 15, panelX + panelWidth - 12, panelY + panelHeight - 13, 0xFFDDD5BA);
            Minecraft mc = Minecraft.getMinecraft();
            double fx = mc.displayWidth / (double) width, fy = mc.displayHeight / (double) height;
            double left = (width - xSize * panelScale) / 2 + (panelX + 1 - guiLeft) * panelScale;
            double bottom = (height - ySize * panelScale) / 2 + (panelY + panelHeight - 16 - guiTop) * panelScale;
            GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor((int)Math.floor(left * fx), (int)Math.floor(mc.displayHeight - bottom * fy),
                    (int)Math.ceil((panelWidth - 2) * panelScale * fx), (int)Math.ceil((panelHeight - 40) * panelScale * fy));
            float zoom = Math.min((panelHeight - 40) / 72F, panelWidth / 75F);
            try { SafeEntityPreview.draw(preview, panelX + 1, panelY + 24,
                    panelWidth - 2, panelHeight - 40, zoom, previewRotation); }
            finally { GL11.glPopAttrib(); }
        } else if (animations != null) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.select.previewUnavailable"), panelX + 8, panelY + 32, 0xFFEEE8D4);
        } else {
            String description = I18n.translateToLocal("cnpcplus.loot.description." + pending);
            int lineY = panelY + 32;
            for (String line : fontRenderer.listFormattedStringToWidth(description, panelWidth - 20)) {
                fontRenderer.drawString(line, panelX + 10, lineY, 0xFFEEE8D4);
                lineY += 10;
            }
        }
        fontRenderer.drawString(fontRenderer.trimStringToWidth(I18n.translateToLocal("cnpcplus.select.confirmHint"), xSize - 176), guiLeft + 12,
                guiTop + ySize - 23, 0x686868);
    }

    @Override public void updateScreen() {
        super.updateScreen();
        if (preview != null) {
            preview.ticksExisted++;
            preview.prevLimbSwingAmount = preview.limbSwingAmount = original.id == 12 ? .6F : 0;
            preview.limbSwing += preview.limbSwingAmount;
        }
    }
}
