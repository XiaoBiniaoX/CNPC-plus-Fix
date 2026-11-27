package top.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.quests.QuestKill;
import noppes.npcs.quests.QuestManual;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiSliderNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ISliderListener;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.QuestAreaKill;
import top.cnpcplus.quest.QuestCraft;
import top.cnpcplus.quest.QuestExplore;
import top.cnpcplus.text.client.PlayerTextPreview;

/** Coordinates and per-output settings for CNPCplus quest types. */
public final class SubGuiQuestSettings extends GuiNPCInterface implements ITextfieldListener, ISliderListener, ICustomScrollListener {
    private static final net.minecraft.resources.ResourceLocation SLOT_TEXTURE =
            new net.minecraft.resources.ResourceLocation("customnpcs", "textures/gui/slot.png");
    private final Quest quest;
    private GuiCustomScrollNop candidatesScroll;
    private java.util.List<Candidate> candidates = java.util.List.of();
    private EntityNPCInterface preview;
    private GuiTextFieldNop selectedTargetField;
    private final java.util.List<String> visibleTargetKeys = new java.util.ArrayList<>(3);
    private record Candidate(String label, String value, EntityNPCInterface npc) {}

    public SubGuiQuestSettings(Quest quest) {
        this.quest = quest;
        if (quest.type == 6 || quest.type == 2 || quest.type == 4) { imageWidth = 420; imageHeight = 240; }
        else if (quest.type == 5) { imageWidth = 360; imageHeight = 230; }
        else if (quest.type == 7) { imageWidth = 380; imageHeight = 240; }
        else { imageWidth = 350; imageHeight = 220; }
        setBackground("menubg.png");
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        if (quest.type == 6) setupCraft((QuestCraft)quest.questInterface);
        else if (quest.type == 7) setupExplore((QuestExplore)quest.questInterface);
        else if (quest.type == 3) setupLocation((noppes.npcs.quests.QuestLocation)quest.questInterface);
        else if (quest.type == 5) setupManual();
        else setupTargets();
    }

    private void setupCraft(QuestCraft craft) {
        for (int i = 0; i < 9; i++) {
            int y = guiTop + 38 + i * 20;
            String count = Integer.toString(craft.required[i]);
            addTextField(new GuiTextFieldNop(i, this, guiLeft + 365, y, 52, 18, count));
            getTextField(i).numbersOnly = true;
            getTextField(i).setMinMaxDefault(1, 1_000_000, 1);
            addButton(new GuiButtonNop(this, 100 + i, guiLeft + 143, y - 1, 180, 20,
                    "cnpcplus.quest.source." + ItemProductionEvent.Source.values()[craft.sources[i].ordinal()].name().toLowerCase(java.util.Locale.ROOT)));
        }
        addButton(new GuiQuestItemSlotButton(this, 20, guiLeft + 12, guiTop + 218,
                new ItemStack(Items.NAME_TAG), Component.translatable("cnpcplus.quest.craft.items")));
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + 218, 74, 20, "gui.done"));
    }

    private void setupTargets() {
        if (quest.type == 4) {
            QuestAreaKill area = (QuestAreaKill)quest.questInterface;
            addCoordinateFields(new int[]{area.x1, area.y1, area.z1, area.x2, area.y2, area.z2}, 6, 10, 42, 34);
        }
        java.util.Map<String, Integer> targets = targetMap();
        visibleTargetKeys.clear();
        int rowTop = quest.type == 4 ? 144 : 64;
        int rowCount = 0;
        for (var entry : targets.entrySet()) {
            if (rowCount == 3) break;
            addTargetRow(rowCount, entry.getKey(), entry.getValue(), rowTop);
            visibleTargetKeys.add(entry.getKey());
            rowCount++;
        }
        while (rowCount < 3) addTargetRow(rowCount++, "", 1, rowTop);
        candidates = getCandidates();
        candidatesScroll = new GuiCustomScrollNop(this, 0);
        candidatesScroll.disabledSearch();
        candidatesScroll.setSize(142, 64);
        candidatesScroll.guiLeft = guiLeft + imageWidth - 154;
        candidatesScroll.guiTop = guiTop + 42;
        candidatesScroll.setUnsortedList(candidates.stream().map(Candidate::label).toList());
        addScroll(candidatesScroll);
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + 212, 74, 20, "gui.done"));
    }

    private void setupManual() {
        java.util.Map<String, Integer> targets = targetMap();
        visibleTargetKeys.clear();
        int rowCount = 0;
        for (var entry : targets.entrySet()) {
            if (rowCount == 3) break;
            addTargetRow(rowCount, entry.getKey(), entry.getValue(), 82);
            visibleTargetKeys.add(entry.getKey());
            rowCount++;
        }
        while (rowCount < 3) addTargetRow(rowCount++, "", 1, 82);
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + imageHeight - 28, 74, 20, "gui.done"));
    }

    private void addTargetRow(int row, String name, int amount, int rowTop) {
        int y = guiTop + rowTop + row * 25;
        int countX = quest.type == 2 || quest.type == 4 ? imageWidth - 208 : 236;
        int nameWidth = quest.type == 2 || quest.type == 4 ? countX - 42 : 212;
        addTextField(new GuiTextFieldNop(row, this, guiLeft + 18, y, nameWidth, 20, name));
        addTextField(new GuiTextFieldNop(row + 3, this, guiLeft + countX, y, 54, 20, Integer.toString(amount)));
        getTextField(row + 3).numbersOnly = true;
        getTextField(row + 3).setMinMaxDefault(1, Integer.MAX_VALUE, 1);
    }

    private java.util.Map<String, Integer> targetMap() {
        return quest.type == 5 ? ((QuestManual)quest.questInterface).manuals : ((QuestKill)quest.questInterface).targets;
    }

    private java.util.List<Candidate> getCandidates() {
        java.util.ArrayList<Candidate> result = new java.util.ArrayList<>();
        result.add(new Candidate(I18n.get("cnpcplus.quest.target.player"), "Player", null));
        if (Minecraft.getInstance().level != null) {
            for (EntityType<?> type : EntityUtil.getAllEntitiesClassesNoNpcs(Minecraft.getInstance().level).keySet()) {
                var key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                if (key != null) result.add(new Candidate(key.toString(), key.toString(), null));
            }
        }
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().player != null) {
            var box = Minecraft.getInstance().player.getBoundingBox().inflate(32);
            for (EntityNPCInterface npc : Minecraft.getInstance().level.getEntitiesOfClass(EntityNPCInterface.class, box)) {
                String name = npc.getName().getString();
                if (!name.isBlank()) result.add(new Candidate(I18n.get("cnpcplus.quest.target.npc", name), name, npc));
            }
        }
        return result;
    }

    private void saveTargets() {
        java.util.TreeMap<String, Integer> updated = new java.util.TreeMap<>(targetMap());
        updated.keySet().removeAll(visibleTargetKeys);
        visibleTargetKeys.clear();
        for (int i = 0; i < 3; i++) {
            String name = getTextField(i).getValue().trim();
            int amount = Math.max(1, getTextField(i + 3).getInteger());
            if (!name.isEmpty()) {
                updated.put(name, amount);
                visibleTargetKeys.add(name);
            }
        }
        if (quest.type == 5) ((QuestManual)quest.questInterface).manuals = updated;
        else if (quest.type == 4) {
            QuestAreaKill area = (QuestAreaKill)quest.questInterface;
            area.targets = updated;
            area.refreshTargets();
        } else ((QuestKill)quest.questInterface).targets = updated;
    }

    private void setupExplore(QuestExplore explore) {
        int[] values = {explore.x1, explore.y1, explore.z1, explore.x2, explore.y2, explore.z2,
                explore.requiredPercent, explore.viewDistance};
        addCoordinateFields(values, 6, 0, 42, 34);
        GuiSliderNop percent = new GuiSliderNop(this, 6, guiLeft + 22, guiTop + 142, 190, 20,
                (explore.requiredPercent - 1) / 79.0F);
        percent.setString(I18n.get("cnpcplus.quest.explore.percent") + " " + explore.requiredPercent + "%");
        addSlider(percent);
        addTextField(new GuiTextFieldNop(7, this, guiLeft + 278, guiTop + 142, 68, 20,
                Integer.toString(explore.viewDistance)));
        getTextField(7).numbersOnly = true;
        getTextField(7).setMinMaxDefault(1, 128, 16);
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + 212, 74, 20, "gui.done"));
    }

    private void setupLocation(noppes.npcs.quests.QuestLocation location) {
        String[] values = {location.location, location.location2, location.location3};
        for (int i = 0; i < values.length; i++)
            addTextField(new GuiTextFieldNop(i, this, guiLeft + 18, guiTop + 68 + i * 34, imageWidth - 36, 20, values[i]));
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + 184, 74, 20, "gui.done"));
    }

    private void addCoordinateFields(int[] values, int count, int firstId, int top, int rowGap) {
        for (int i = 0; i < count; i++) {
            int col = i < 6 ? i % 3 : i - 6;
            int row = i < 6 ? i / 3 : 2;
            int x = guiLeft + (i < 6 ? 22 + col * 108 : 22 + col * 158);
            int y = guiTop + top + row * rowGap;
            int id = firstId + i;
            addTextField(new GuiTextFieldNop(id, this, x + (i < 6 ? 26 : 36), y, 72, 20, Integer.toString(values[i])));
            getTextField(id).numbersOnly = true;
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        int id = field.id;
        if (quest.type == 6 && id >= 0 && id < 9) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            if (field.isInteger()) craft.required[id] = Math.max(1, Math.min(1_000_000, field.getInteger()));
        } else if (quest.type == 3 && id >= 0 && id < 3) {
            noppes.npcs.quests.QuestLocation location = (noppes.npcs.quests.QuestLocation)quest.questInterface;
            if (id == 0) location.location = field.getValue();
            else if (id == 1) location.location2 = field.getValue();
            else location.location3 = field.getValue();
        } else if ((quest.type == 2 || quest.type == 4 || quest.type == 5) && id >= 0 && id < 6) {
            saveTargets();
        } else if (quest.type == 7 && id == 7 && field.isInteger()) {
            ((QuestExplore)quest.questInterface).viewDistance = Math.max(1, Math.min(128, field.getInteger()));
        } else if (field.isInteger() && ((quest.type == 4 && id >= 10 && id < 16) || (quest.type == 7 && id >= 0 && id < 6))) {
            int value = field.getInteger();
            if (quest.type == 4) setCoordinate((QuestAreaKill)quest.questInterface, id - 10, value);
            else setExplore((QuestExplore)quest.questInterface, id, value);
        }
    }

    private static void setCoordinate(QuestAreaKill q, int id, int value) {
        switch (id) {
            case 0 -> q.x1 = value; case 1 -> q.y1 = value; case 2 -> q.z1 = value;
            case 3 -> q.x2 = value; case 4 -> q.y2 = value; case 5 -> q.z2 = value;
        }
    }

    private static void setExplore(QuestExplore q, int id, int value) {
        switch (id) {
            case 0 -> q.x1 = value; case 1 -> q.y1 = value; case 2 -> q.z1 = value;
            case 3 -> q.x2 = value; case 4 -> q.y2 = value; case 5 -> q.z2 = value;
            case 6 -> q.requiredPercent = value; case 7 -> q.viewDistance = value;
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        GuiTextFieldNop.unfocus();
        if (quest.type == 6 && button.id >= 100 && button.id < 109) {
            setSubGui(new SubGuiQuestCraftSource((QuestCraft)quest.questInterface, button.id - 100));
        } else if (button.id == 20) {
            setSubGui(new SubGuiQuestCraftItems((QuestCraft)quest.questInterface));
        } else if (button.id == 21) {
            if (quest.type == 2 || quest.type == 4 || quest.type == 5) saveTargets();
            if (quest.type == 4) ((QuestAreaKill)quest.questInterface).normalize();
            else if (quest.type == 7) ((QuestExplore)quest.questInterface).normalize();
            close();
        }
    }

    @Override
    public void mouseDragged(GuiSliderNop slider) {
        int percent = Math.max(1, Math.min(80, 1 + Math.round(slider.sliderValue * 79.0F)));
        ((QuestExplore)quest.questInterface).requiredPercent = percent;
        slider.setString(I18n.get("cnpcplus.quest.explore.percent") + " " + percent + "%");
    }

    @Override public void mousePressed(GuiSliderNop slider) {}
    @Override public void mouseReleased(GuiSliderNop slider) {}

    @Override
    public boolean m_6375_(double x, double y, int button) {
        boolean handled = super.m_6375_(x, y, button);
        if ((quest.type == 2 || quest.type == 4 || quest.type == 5)
                && GuiTextFieldNop.isAnyActive() && GuiTextFieldNop.getActive().id >= 0 && GuiTextFieldNop.getActive().id < 3)
            selectedTargetField = GuiTextFieldNop.getActive();
        return handled;
    }

    @Override
    public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {
        int index = scroll.getSelectedIndex();
        if (index < 0 || index >= candidates.size()) return;
        Candidate candidate = candidates.get(index);
        if (selectedTargetField != null) {
            selectedTargetField.setValue(candidate.value());
            saveTargets();
        }
        preview = null;
        if (candidate.npc() != null) {
            var snapshot = candidate.npc().writeSpawnData().copy();
            snapshot.putString("CNPCPlusPreviewType", BuiltInRegistries.ENTITY_TYPE.getKey(candidate.npc().getType()).toString());
            preview = PlayerTextPreview.npc(snapshot);
        }
    }

    @Override public void scrollDoubleClicked(String selected, GuiCustomScrollNop scroll) {
        scrollClicked(0, 0, 0, scroll);
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
        if (hasSubGui()) return;
        if (quest.type == 6) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.title"), guiLeft + 12, guiTop + 10, 0x202020, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.output"), guiLeft + 12, guiTop + 24, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.source.label"), guiLeft + 143, guiTop + 24, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.targets.amount"), guiLeft + 358, guiTop + 24, 0x555555, false);
            for (int i = 0; i < 9; i++) {
                int x = guiLeft + 12, y = guiTop + 38 + i * 20;
                ItemStack target = craft.items.items.get(i);
                graphics.fill(x, y, x + 20, y + 20, 0xff171717);
                graphics.blit(SLOT_TEXTURE, x + 1, y + 1, 0, 0, 18, 18);
                if (!target.isEmpty()) graphics.renderItem(target, x + 2, y + 2);
                String name = target.isEmpty() ? I18n.get("cnpcplus.quest.craft.unset") : target.getHoverName().getString();
                graphics.drawString(font, font.plainSubstrByWidth(name, 97), guiLeft + 36, y + 6, 0x303030, false);
                if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20 && !target.isEmpty())
                    graphics.renderTooltip(font, target, mouseX, mouseY);
            }
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.items"), guiLeft + 39, guiTop + 224, 0x303030, false);
        } else if (quest.type == 2 || quest.type == 4) {
            graphics.drawString(font, I18n.get(quest.type == 2 ? "cnpcplus.quest.kill.title" : "cnpcplus.quest.area.title"),
                    guiLeft + 12, guiTop + 12, 0x202020, false);
            if (quest.type == 4) {
                graphics.drawString(font, I18n.get("cnpcplus.quest.area.bounds"), guiLeft + 22, guiTop + 24, 0x555555, false);
                drawCoordinateLabels(graphics, 42, 34);
                graphics.drawString(font, I18n.get("cnpcplus.quest.area.targets"), guiLeft + 18, guiTop + 124, 0x555555, false);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.name"), guiLeft + 18, guiTop + 134, 0x555555, false);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.amount"), guiLeft + imageWidth - 208, guiTop + 134, 0x555555, false);
            } else {
                graphics.drawString(font, I18n.get("cnpcplus.quest.kill.help"), guiLeft + 18, guiTop + 36, 0x555555, false);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.name"), guiLeft + 18, guiTop + 50, 0x555555, false);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.amount"), guiLeft + imageWidth - 208, guiTop + 50, 0x555555, false);
            }
            graphics.drawString(font, I18n.get("cnpcplus.quest.targets.candidates"), guiLeft + imageWidth - 154, guiTop + 24, 0x555555, false);
            if (preview != null) {
                int px = guiLeft + imageWidth - 154, py = guiTop + 118;
                graphics.fill(px - 1, py - 1, px + 144, py + 72, 0xffa49d89);
                graphics.fill(px, py, px + 142, py + 70, 0xff343b40);
                SafeEntityPreview.nativeDisplay(graphics, preview, 71, 39, 0.8f, 180, mouseX, mouseY, px, py, true, false);
            } else {
                graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.targets.preview.help"),
                        guiLeft + imageWidth - 152, guiTop + 122, 138, 0x686868);
            }
        } else if (quest.type == 5) {
            graphics.drawString(font, I18n.get("cnpcplus.quest.manual.title"), guiLeft + 12, guiTop + 12, 0x202020, false);
            graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.manual.help"), guiLeft + 18, guiTop + 36, imageWidth - 36, 0x555555);
            graphics.drawString(font, I18n.get("cnpcplus.quest.manual.key"), guiLeft + 18, guiTop + 68, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.targets.amount"), guiLeft + 236, guiTop + 68, 0x555555, false);
        } else if (quest.type == 3) {
            graphics.drawString(font, I18n.get("cnpcplus.quest.location.title"), guiLeft + 12, guiTop + 14, 0x202020, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.location.prompt"), guiLeft + 18, guiTop + 36, 0x303030, false);
            for (int i = 0; i < 3; i++)
                graphics.drawString(font, I18n.get("cnpcplus.quest.location.marker") + " " + (i + 1), guiLeft + 18,
                        guiTop + 56 + i * 34, 0x303030, false);
        } else {
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.title"),
                    guiLeft + 12, guiTop + 14, 0x202020, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.bounds"), guiLeft + 22, guiTop + 26, 0x555555, false);
            drawCoordinateLabels(graphics, 42, 34);
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.distance"), guiLeft + 230, guiTop + 148, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.distance.help"), guiLeft + 22, guiTop + 176, 0x686868, false);
            if (Minecraft.getInstance().level != null) {
                QuestExplore q = (QuestExplore)quest.questInterface;
                long cells = q.cellCount(Minecraft.getInstance().level.getMinBuildHeight(), Minecraft.getInstance().level.getMaxBuildHeight());
                if (cells <= 0 || cells > QuestExplore.MAX_CELLS)
                    graphics.drawString(font, I18n.get("cnpcplus.quest.explore.invalid"), guiLeft + 22, guiTop + 198, 0xff3030, false);
                else if (cells == 1)
                    graphics.drawString(font, I18n.get("cnpcplus.quest.explore.small"), guiLeft + 22, guiTop + 198, 0xff9a6a18, false);
                else graphics.drawString(font, I18n.get("cnpcplus.quest.explore.cells", cells), guiLeft + 22, guiTop + 198, 0x686868, false);
            }
        }
    }

    private void drawCoordinateLabels(GuiGraphics graphics, int top, int rowGap) {
        String[] labels = {"X1", "Y1", "Z1", "X2", "Y2", "Z2"};
        for (int i = 0; i < 6; i++) {
            int x = guiLeft + 22 + (i % 3) * 108;
            int y = guiTop + top + (i / 3) * rowGap;
            graphics.drawString(font, labels[i], x, y + 6, 0x303030, false);
        }
    }
}
