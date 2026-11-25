package top.cnpcplus.client.gui;

import java.util.ArrayList;
import java.util.List;
import bin.cnpcplus.script.client.NativeSearchField;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.*;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import noppes.npcs.shared.client.gui.listeners.ISliderListener;
import top.cnpcplus.mixin.GuiButtonOptionsAccess;
import top.cnpcplus.text.client.PlayerTextPreview;
import top.cnpcplus.wand.WandTools;

/** Detached selection: browsing never changes the editor, and Escape is always cancel. */
public final class SubGuiNpcOptionSelect extends GuiBasic implements ICustomScrollListener, ISliderListener {
    private final IGuiInterface parent;
    private final GuiButtonNop original;
    private final String titleKey;
    private final String[] options;
    private final int[] animations;
    private final EntityNPCInterface preview;
    private final List<Integer> visible = new ArrayList<>();
    private GuiCustomScrollNop list;
    private int pending, listWidth;
    private int previewRotation = 150;
    private String search = "";

    public SubGuiNpcOptionSelect(IGuiInterface parent, GuiButtonNop original, String titleKey,
            EntityNPCInterface npc, int[] animations) {
        this.parent = parent;
        this.original = original;
        this.titleKey = titleKey;
        this.animations = animations;
        options = ((GuiButtonOptionsAccess)original).cnpcplus$options().clone();
        pending = original.getValue();
        if (npc == null) preview = null;
        else {
            var snapshot = npc.writeSpawnData();
            snapshot.putString("CNPCPlusPreviewType", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).toString());
            preview = PlayerTextPreview.npc(snapshot);
        }
        setBackground("menubg.png");
    }

    @Override public void init() {
        imageWidth = Math.min(390, width - 12);
        imageHeight = Math.min(236, height - 12);
        super.init();
        listWidth = (imageWidth - 32) / 2;
        addTextField(new NativeSearchField(0, this, guiLeft + 12, guiTop + 32, listWidth, search));
        addWidget(getTextField(0));
        getTextField(0).setResponder(value -> { search = value; filter(); });
        list = new GuiCustomScrollNop(this, 0);
        list.disabledSearch();
        list.setSize(listWidth, imageHeight - 94);
        list.guiLeft = guiLeft + 12;
        list.guiTop = guiTop + 56;
        addScroll(list);
        if (animations != null) {
            addSlider(new GuiSliderNop(this, 2, guiLeft + listWidth + 22, guiTop + imageHeight - 60,
                    imageWidth - listWidth - 34, 20, previewRotation / 360f));
        }
        addButton(new GuiButtonNop(this, 0, guiLeft + imageWidth - 152, guiTop + imageHeight - 28, 68, 20, "gui.done"));
        addButton(new GuiButtonNop(this, 1, guiLeft + imageWidth - 80, guiTop + imageHeight - 28, 68, 20, "gui.cancel"));
        filter();
    }

    private void filter() {
        visible.clear();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < options.length; i++) {
            String label = I18n.get(options[i]);
            if (WandTools.matches(search, label) || WandTools.matches(search, options[i])) {
                visible.add(i);
                labels.add(label);
            }
        }
        list.setUnsortedList(labels);
        list.setSelectedIndex(visible.indexOf(pending));
        getButton(0).setEnabled(visible.contains(pending));
    }

    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {
        int index = list.getSelectedIndex();
        if (index >= 0) pending = visible.get(index);
        getButton(0).setEnabled(index >= 0);
    }
    @Override public void scrollDoubleClicked(String selected, GuiCustomScrollNop scroll) {
        scrollClicked(0, 0, 0, scroll);
    }
    @Override public void buttonEvent(GuiButtonNop button) {
        if (button.id == 0 && visible.contains(pending)) {
            original.setDisplay(pending);
            parent.buttonEvent(original);
        }
        close();
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (getTextField(0).isFocused()) {
            setFocused(getTextField(0));
            setDragging(button == 0);
        }
        return handled;
    }
    @Override public void mouseDragged(GuiSliderNop slider) {
        previewRotation = Math.round(slider.sliderValue * 360);
        slider.setString(I18n.get("cnpcplus.select.rotation", previewRotation));
    }
    @Override public void mousePressed(GuiSliderNop slider) {}
    @Override public void mouseReleased(GuiSliderNop slider) {}
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        g.drawString(font, I18n.get(titleKey), guiLeft + 12, guiTop + 10, 0x202020, false);
        g.drawString(font, font.plainSubstrByWidth(I18n.get("cnpcplus.select.current", I18n.get(options[original.getValue()])), imageWidth - 24),
                guiLeft + 12, guiTop + 22, 0x686868, false);
        int px = guiLeft + listWidth + 22, py = guiTop + 32, pw = imageWidth - listWidth - 34;
        int ph = imageHeight - (animations == null ? 70 : 98);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xffa49d89);
        g.fill(px, py, px + pw, py + ph, 0xff343b40);
        g.drawCenteredString(font, I18n.get(options[pending]), px + pw / 2, py + 8, 0xffeee8d4);
        boolean rendered = false;
        if (animations != null && preview != null) {
            preview.currentAnimation = animations[pending];
            preview.ais.animationType = animations[pending];
            g.fill(px + 12, py + ph - 15, px + pw - 12, py + ph - 13, 0xffddd5ba);
            g.enableScissor(px + 1, py + 24, px + pw - 1, py + ph - 16);
            try {
                rendered = SafeEntityPreview.nativeDisplay(g, preview, pw / 2, ph - 20,
                        Math.min((ph - 40) / 72f, pw / 75f), previewRotation, 0, 0, px, py, false, false);
            } finally { g.disableScissor(); }
        } else if (animations == null) {
            g.drawWordWrap(font, net.minecraft.network.chat.Component.translatable("cnpcplus.loot.description." + pending),
                    px + 10, py + 32, pw - 20, 0xffeee8d4);
        }
        if (animations != null && !rendered) g.drawWordWrap(font,
                net.minecraft.network.chat.Component.translatable("cnpcplus.select.previewUnavailable"),
                px + 10, py + 32, pw - 20, 0xffeee8d4);
        g.drawString(font, font.plainSubstrByWidth(I18n.get("cnpcplus.select.confirmHint"), imageWidth - 176),
                guiLeft + 12, guiTop + imageHeight - 23, 0x686868, false);
    }
    @Override public void tick() {
        super.tick();
        if (preview != null) {
            preview.tickCount++;
            preview.walkAnimation.update(original.id == 12 ? 0.6f : 0, 1);
        }
    }
}
