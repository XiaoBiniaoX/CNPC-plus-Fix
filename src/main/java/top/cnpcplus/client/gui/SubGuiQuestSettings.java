package top.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.quests.QuestKill;
import noppes.npcs.quests.QuestManual;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiSliderNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import noppes.npcs.shared.client.gui.listeners.ISliderListener;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import top.cnpcplus.quest.CraftStation;
import top.cnpcplus.quest.QuestAreaKill;
import top.cnpcplus.quest.QuestCraft;
import top.cnpcplus.quest.QuestExplore;
import top.cnpcplus.text.client.PlayerTextPreview;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Coordinates and per-output settings for CNPCplus quest types. */
public final class SubGuiQuestSettings extends GuiQuestPanel implements ITextfieldListener, ISliderListener, ICustomScrollListener {
    private final Quest quest;
    private GuiCustomScrollNop candidatesScroll;
    private java.util.List<Candidate> candidates = java.util.List.of();
    private LivingEntity nearbyPreview;
    private GuiTextFieldNop selectedTargetField;
    private int selectedCraftSlot;
    private int selectedTargetRow;
    private String targetSearch = "";
    private final List<String> visibleTargetKeys = new ArrayList<>(3);
    private final List<Candidate> visibleCandidates = new ArrayList<>();
    private final List<GuiQuestItemSlotButton> craftSlotButtons = new ArrayList<>(9);
    private final List<StationIconButton> stationIconButtons = new ArrayList<>(CraftStation.values().length);
    private final List<RecipeRowButton> recipeRowButtons = new ArrayList<>(6);
    private final List<QuestCraftRecipe> visibleRecipes = new ArrayList<>();
    private CraftStation selectedStation = CraftStation.WORKBENCH;
    private QuestCraftRecipe selectedRecipe;
    private String recipeSearch = "";
    private int recipeScrollOffset;
    private boolean draggingRecipeScrollbar;
    private record Candidate(String label, String value, LivingEntity preview) {}

    public SubGuiQuestSettings(Quest quest) {
        this.quest = quest;
        if (quest.type == 6) { imageWidth = 600; imageHeight = 340; }
        else if (quest.type == 2) { imageWidth = 560; imageHeight = 300; }
        else if (quest.type == 4) { imageWidth = 560; imageHeight = 320; }
        else if (quest.type == 5) { imageWidth = 360; imageHeight = 230; }
        else if (quest.type == 7) { imageWidth = 380; imageHeight = 320; }
        else { imageWidth = 350; imageHeight = 220; }
        if (quest.type == 6) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            for (int i = 0; i < 9; i++) {
                if (craft.configured(i)) {
                    selectedCraftSlot = i;
                    break;
                }
            }
            if (craft.configured(selectedCraftSlot)) {
                selectedStation = CraftStation.byKey(craft.stations[selectedCraftSlot]);
                selectedRecipe = new QuestCraftRecipe(craft.stations[selectedCraftSlot],
                        craft.recipeIds[selectedCraftSlot], craft.recipeName(selectedCraftSlot),
                        craft.outputs[selectedCraftSlot], List.of());
            }
        }
    }

    @Override
    public void init() {
        super.init();
        if (quest.type == 6) setupCraft((QuestCraft)quest.questInterface);
        else if (quest.type == 7) setupExplore((QuestExplore)quest.questInterface);
        else if (quest.type == 3) setupLocation((noppes.npcs.quests.QuestLocation)quest.questInterface);
        else if (quest.type == 5) setupManual();
        else setupTargets();
    }

    private void setupCraft(QuestCraft craft) {
        QuestCraftRecipeCatalog.requestMissingLists();
        craftSlotButtons.clear();
        stationIconButtons.clear();
        recipeRowButtons.clear();
        for (int i = 0; i < 9; i++) {
            GuiQuestItemSlotButton slot = new GuiQuestItemSlotButton(this, 30 + i,
                    guiLeft + 20 + i % 3 * 32, guiTop + 55 + i / 3 * 32,
                    QuestCraft.previewStack(craft.outputs[i], craft.required[i]),
                    Component.translatable("cnpcplus.quest.craft.recipe.empty_slot", i + 1));
            slot.setSelected(i == selectedCraftSlot);
            addButton(slot);
            craftSlotButtons.add(slot);
        }
        boolean hasRecipe = craft.configured(selectedCraftSlot);
        addTextField(new GuiTextFieldNop(0, this, guiLeft + 20, guiTop + 245, 112, 20,
                Integer.toString(craft.required[selectedCraftSlot])));
        getTextField(0).numbersOnly = true;
        getTextField(0).setMinMaxDefault(1, 1_000_000, 1);
        getTextField(0).enabled = hasRecipe;
        for (int i = 0; i < CraftStation.values().length; i++) {
            CraftStation station = CraftStation.values()[i];
            StationIconButton icon = new StationIconButton(this, 200 + i, guiLeft + 168 + i % 6 * 32,
                    guiTop + 38 + i / 6 * 28, station);
            icon.setSelected(station == selectedStation);
            addButton(icon);
            stationIconButtons.add(icon);
        }
        NativeSearchField searchField = new NativeSearchField(90, this, guiLeft + 168, guiTop + 96, 246, recipeSearch);
        searchField.setMaxLength(128);
        searchField.setHint(Component.translatable("cnpcplus.quest.craft.recipe.search"));
        searchField.setResponder(value -> { recipeSearch = value; recipeScrollOffset = 0; refreshRecipeList(); });
        addTextField(searchField);
        for (int i = 0; i < 6; i++) {
            RecipeRowButton row = new RecipeRowButton(this, 300 + i,
                    guiLeft + 168, guiTop + 126 + i * 27, 246, 24);
            row.visible = false;
            addButton(row);
            recipeRowButtons.add(row);
        }
        addButton(new GuiButtonNop(this, 20, guiLeft + 20, guiTop + 274, 112, 20, "cnpcplus.quest.craft.recipe.clear"));
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + imageHeight - 28, 74, 20, "gui.done"));
        refreshRecipeList();
    }

    private void setupTargets() {
        if (quest.type == 4) {
            QuestAreaKill area = (QuestAreaKill)quest.questInterface;
            addCoordinateFields(new int[]{area.x1, area.y1, area.z1, area.x2, area.y2, area.z2}, 6, 10, 42, 34);
        }
        java.util.Map<String, Integer> targets = targetMap();
        visibleTargetKeys.clear();
        int rowTop = quest.type == 4 ? 148 : 82;
        int rowCount = 0;
        for (var entry : targets.entrySet()) {
            if (rowCount == 3) break;
            addTargetRow(rowCount, entry.getKey(), entry.getValue(), rowTop);
            visibleTargetKeys.add(entry.getKey());
            rowCount++;
        }
        while (rowCount < 3) addTargetRow(rowCount++, "", 1, rowTop);
        candidates = getCandidates();
        NativeSearchField searchField = new NativeSearchField(90, this,
                guiLeft + imageWidth - 202, guiTop + 42, 188, targetSearch);
        searchField.setMaxLength(128);
        searchField.setHint(Component.translatable("cnpcplus.quest.targets.search"));
        searchField.setResponder(value -> { targetSearch = value; refreshCandidateList(); });
        addTextField(searchField);
        candidatesScroll = new GuiCustomScrollNop(this, 0);
        candidatesScroll.disabledSearch();
        candidatesScroll.setSize(188, quest.type == 4 ? 116 : 100);
        candidatesScroll.guiLeft = guiLeft + imageWidth - 202;
        candidatesScroll.guiTop = guiTop + 66;
        addScroll(candidatesScroll);
        refreshCandidateList();
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + imageHeight - 28, 74, 20, "gui.done"));
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
        int countX = quest.type == 2 || quest.type == 4 ? imageWidth - 274 : 236;
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
        ArrayList<Candidate> result = new ArrayList<>();
        var player = Minecraft.getInstance().player;
        var level = Minecraft.getInstance().level;
        if (player == null || level == null) return result;
        result.add(new Candidate(I18n.get("cnpcplus.quest.target.player"), "Player", player));
        var nearby = player.getBoundingBox().inflate(128);
        java.util.HashSet<String> entityTypes = new java.util.HashSet<>();
        java.util.HashSet<String> npcNames = new java.util.HashSet<>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, nearby)) {
            if (entity == player || !entity.isAlive() || entity.distanceToSqr(player) > 128.0 * 128.0) continue;
            if (entity instanceof EntityNPCInterface npc) {
                String name = npc.getName().getString();
                if (name.isBlank() || !npcNames.add(name)) continue;
                var snapshot = npc.writeSpawnData().copy();
                snapshot.putString("CNPCPlusPreviewType", BuiltInRegistries.ENTITY_TYPE.getKey(npc.getType()).toString());
                result.add(new Candidate(I18n.get("cnpcplus.quest.target.npc", name), name,
                        PlayerTextPreview.npc(snapshot)));
            } else {
                var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                if (key != null && entityTypes.add(key.toString()))
                    result.add(new Candidate(key.toString(), key.toString(), entity));
            }
        }
        return result;
    }

    private void refreshCandidateList() {
        if (candidatesScroll == null) return;
        String query = targetSearch.trim().toLowerCase(Locale.ROOT);
        visibleCandidates.clear();
        for (Candidate candidate : candidates) {
            if (query.isEmpty() || candidate.label().toLowerCase(Locale.ROOT).contains(query)
                    || candidate.value().toLowerCase(Locale.ROOT).contains(query)) visibleCandidates.add(candidate);
        }
        candidatesScroll.setUnsortedList(visibleCandidates.stream()
                .map(candidate -> candidate.label().replace("%", "%%")).toList());
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
        percent.setString(explore.requiredPercent + "%");
        addSlider(percent);
        addTextField(new GuiTextFieldNop(7, this, guiLeft + 278, guiTop + 142, 68, 20,
                Integer.toString(explore.viewDistance)));
        getTextField(7).numbersOnly = true;
        getTextField(7).setMinMaxDefault(1, 128, 16);
        addTextField(new GuiTextFieldNop(8, this, guiLeft + 22, guiTop + 188,
                imageWidth - 44, 20, explore.getProgressText()));
        getTextField(8).setMaxLength(64);
        addButton(new GuiButtonNop(this, 22, guiLeft + 22, guiTop + 228, 250, 20, explore.coverageModeKey()));
        addButton(new GuiButtonNop(this, 23, guiLeft + 280, guiTop + 228, 78, 20,
                "cnpcplus.quest.explore.coverage.help"));
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + 292, 74, 20, "gui.done"));
    }

    private void setupLocation(noppes.npcs.quests.QuestLocation location) {
        String[] values = {location.location, location.location2, location.location3};
        for (int i = 0; i < values.length; i++)
            addTextField(new GuiTextFieldNop(i, this, guiLeft + 18, guiTop + 68 + i * 34, imageWidth - 36, 20, values[i]));
        addButton(new GuiButtonNop(this, 21, guiLeft + imageWidth - 86, guiTop + 184, 74, 20, "gui.done"));
    }

    private void addCoordinateFields(int[] values, int count, int firstId, int top, int rowGap) {
        boolean compact = quest.type == 4;
        int step = compact ? 82 : 108;
        for (int i = 0; i < count; i++) {
            int col = i < 6 ? i % 3 : i - 6;
            int row = i < 6 ? i / 3 : 2;
            int x = guiLeft + (i < 6 ? 22 + col * step : 22 + col * 158);
            int y = guiTop + top + row * rowGap;
            int id = firstId + i;
            addTextField(new GuiTextFieldNop(id, this, x + (i < 6 ? 26 : 36), y,
                    compact ? 50 : 72, 20, Integer.toString(values[i])));
            getTextField(id).setFilter(QuestExplore::coordinateInputAllowed);
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        int id = field.id;
        if (quest.type == 6 && id == 0) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            if (field.isInteger()) craft.required[selectedCraftSlot] = Math.max(1, Math.min(1_000_000, field.getInteger()));
        } else if (quest.type == 3 && id >= 0 && id < 3) {
            noppes.npcs.quests.QuestLocation location = (noppes.npcs.quests.QuestLocation)quest.questInterface;
            if (id == 0) location.location = field.getValue();
            else if (id == 1) location.location2 = field.getValue();
            else location.location3 = field.getValue();
        } else if ((quest.type == 2 || quest.type == 4 || quest.type == 5) && id >= 0 && id < 6) {
            saveTargets();
        } else if ((quest.type == 2 || quest.type == 4) && id == 90) {
            targetSearch = field.getValue();
        } else if (quest.type == 7 && id == 7 && field.isInteger()) {
            ((QuestExplore)quest.questInterface).viewDistance = Math.max(1, Math.min(128, field.getInteger()));
        } else if (quest.type == 7 && id == 8) {
            ((QuestExplore)quest.questInterface).setProgressText(field.getValue());
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

    private void refreshRecipeList() {
        if (quest.type != 6 || selectedStation == null) return;
        QuestCraft craft = (QuestCraft)quest.questInterface;
        if (craft.configured(selectedCraftSlot)) {
            for (QuestCraftRecipe recipe : QuestCraftRecipeCatalog.recipes(craft.stations[selectedCraftSlot])) {
                if (recipe.id().equals(craft.recipeIds[selectedCraftSlot])) {
                    selectedRecipe = recipe;
                    break;
                }
            }
        }
        String query = recipeSearch.trim().toLowerCase(Locale.ROOT);
        visibleRecipes.clear();
        for (QuestCraftRecipe recipe : QuestCraftRecipeCatalog.recipes(selectedStation.key)) {
            if (query.isEmpty() || recipe.name().toLowerCase(Locale.ROOT).contains(query)
                    || recipe.id().toLowerCase(Locale.ROOT).contains(query)
                    || recipe.output().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
                visibleRecipes.add(recipe);
        }
        recipeScrollOffset = Math.max(0, Math.min(recipeScrollOffset, Math.max(0, visibleRecipes.size() - 6)));
        for (int i = 0; i < recipeRowButtons.size(); i++) {
            int index = recipeScrollOffset + i;
            RecipeRowButton row = recipeRowButtons.get(i);
            row.visible = index < visibleRecipes.size();
            QuestCraftRecipe recipe = row.visible ? visibleRecipes.get(index) : null;
            boolean selected = recipe != null && selectedRecipe != null
                    && selectedRecipe.station().equals(recipe.station()) && selectedRecipe.id().equals(recipe.id());
            row.setRecipe(recipe, selected);
        }
        updateStationIcons();
    }

    private void updateCraftSlotButtons() {
        QuestCraft craft = (QuestCraft)quest.questInterface;
        for (int i = 0; i < craftSlotButtons.size(); i++) {
            craftSlotButtons.get(i).setItem(QuestCraft.previewStack(craft.outputs[i], craft.required[i]));
            craftSlotButtons.get(i).setSelected(i == selectedCraftSlot);
        }
    }

    private void updateStationIcons() {
        for (StationIconButton button : stationIconButtons) button.setSelected(button.station == selectedStation);
    }

    public static void refreshCraftRecipes() {
        if (Minecraft.getInstance().screen instanceof IGuiInterface parent
                && parent.getSubGui() instanceof SubGuiQuestSettings settings && settings.quest.type == 6)
            settings.refreshRecipeList();
    }

    private static final class StationIconButton extends GuiButtonNop {
        private final CraftStation station;
        private final ItemStack icon;
        private boolean selected;

        StationIconButton(SubGuiQuestSettings gui, int id, int x, int y, CraftStation station) {
            super(gui, id, x, y, 24, 24, " ");
            this.station = station;
            this.icon = new ItemStack(stationIcon(station));
        }

        void setSelected(boolean value) { selected = value; }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible) return;
            int x = getX(), y = getY();
            var font = Minecraft.getInstance().font;
            graphics.fill(x, y, x + 24, y + 24, selected ? 0xff555555 : 0xff777777);
            graphics.fill(x + 1, y + 1, x + 23, y + 23,
                    selected ? 0xffeeeeee : isHoveredOrFocused() ? 0xffd8d8d8 : 0xffc0c0c0);
            graphics.renderItem(icon, x + 4, y + 4);
            if (selected) graphics.renderOutline(x - 1, y - 1, 26, 26, 0xfff4f0dc);
            if (isHovered()) graphics.renderTooltip(font, Component.translatable(station.translationKey), mouseX, mouseY);
        }

        private static net.minecraft.world.item.Item stationIcon(CraftStation station) {
            return switch (station) {
                case WORKBENCH -> Items.CRAFTING_TABLE;
                case CARPENTRY -> Items.OAK_PLANKS;
                case ANVIL -> Items.ANVIL;
                case SMITHING -> Items.SMITHING_TABLE;
                case FURNACE -> Items.FURNACE;
                case BLAST_FURNACE -> Items.BLAST_FURNACE;
                case SMOKER -> Items.SMOKER;
                case GRINDSTONE -> Items.GRINDSTONE;
                case BREWING -> Items.BREWING_STAND;
                case CAMPFIRE -> Items.CAMPFIRE;
                case DROP -> Items.DROPPER;
            };
        }
    }

    private static final class RecipeRowButton extends GuiButtonNop {
        private QuestCraftRecipe recipe;
        private boolean selected;

        RecipeRowButton(SubGuiQuestSettings gui, int id, int x, int y, int width, int height) {
            super(gui, id, x, y, width, height, " ");
        }

        void setRecipe(QuestCraftRecipe recipe, boolean selected) {
            this.recipe = recipe;
            this.selected = selected;
        }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible || recipe == null) return;
            int x = getX(), y = getY();
            graphics.fill(x, y, x + width, y + height, selected ? 0xff555555 : 0xff777777);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    isHoveredOrFocused() ? 0xffd8d8d8 : 0xffc0c0c0);
            ItemStack output = recipe.output();
            graphics.renderItem(output, x + 4, y + 4);
            graphics.renderItemDecorations(Minecraft.getInstance().font, output, x + 4, y + 4);
            String name = Minecraft.getInstance().font.plainSubstrByWidth(recipe.name(), width - 54);
            graphics.drawString(Minecraft.getInstance().font, name, x + 26, y + 8, 0xff202020, false);
            graphics.drawString(Minecraft.getInstance().font, "×" + output.getCount(), x + width - 26, y + 8, 0xff404040, false);
            if (isHovered()) {
                graphics.renderTooltip(Minecraft.getInstance().font,
                        Component.literal(recipe.name()).withStyle(ChatFormatting.YELLOW), mouseX, mouseY);
                graphics.renderOutline(x, y, width, height, 0xfff4f0dc);
            }
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        GuiTextFieldNop.unfocus();
        if (quest.type == 6 && button.id >= 30 && button.id < 39) {
            selectedCraftSlot = button.id - 30;
            QuestCraft craft = (QuestCraft)quest.questInterface;
            if (craft.configured(selectedCraftSlot)) {
                selectedStation = CraftStation.byKey(craft.stations[selectedCraftSlot]);
                selectedRecipe = new QuestCraftRecipe(craft.stations[selectedCraftSlot],
                        craft.recipeIds[selectedCraftSlot], craft.recipeName(selectedCraftSlot),
                        craft.outputs[selectedCraftSlot], List.of());
            } else selectedRecipe = null;
            recipeScrollOffset = 0;
            init();
        } else if (quest.type == 6 && button.id >= 200 && button.id < 200 + CraftStation.values().length) {
            selectedStation = CraftStation.values()[button.id - 200];
            recipeScrollOffset = 0;
            refreshRecipeList();
        } else if (quest.type == 6 && button.id >= 300 && button.id < 306) {
            int index = recipeScrollOffset + button.id - 300;
            if (index < visibleRecipes.size()) {
                selectedRecipe = visibleRecipes.get(index);
                ((QuestCraft)quest.questInterface).setRecipe(selectedCraftSlot, selectedRecipe.station(),
                        selectedRecipe.id(), selectedRecipe.name(), selectedRecipe.output());
                getTextField(0).enabled = true;
                updateCraftSlotButtons();
                refreshRecipeList();
            }
        } else if (quest.type == 6 && button.id == 20) {
            ((QuestCraft)quest.questInterface).clearRecipe(selectedCraftSlot);
            selectedRecipe = null;
            getTextField(0).enabled = false;
            updateCraftSlotButtons();
            refreshRecipeList();
        } else if (quest.type == 7 && button.id == 22) {
            GuiTextFieldNop.unfocus();
            QuestExplore explore = (QuestExplore)quest.questInterface;
            explore.horizontalCoverage = !explore.horizontalCoverage;
            button.setDisplayText(explore.coverageModeKey());
        } else if (quest.type == 7 && button.id == 23) {
            GuiTextFieldNop.unfocus();
            setSubGui(new SubGuiQuestExploreCoverageHelp());
        } else if (button.id == 21) {
            if (quest.type == 7) GuiTextFieldNop.unfocus();
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
        slider.setString(percent + "%");
    }

    @Override public void mousePressed(GuiSliderNop slider) {}
    @Override public void mouseReleased(GuiSliderNop slider) {}

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        boolean handled = super.mouseClicked(x, y, button);
        if (quest.type == 6 && button == 0 && x >= guiLeft + 416 && x < guiLeft + 422
                && y >= guiTop + 126 && y < guiTop + 288) {
            draggingRecipeScrollbar = true;
            updateRecipeScrollFromMouse(y);
            return true;
        }
        if ((quest.type == 2 || quest.type == 4 || quest.type == 5)
                && GuiTextFieldNop.isAnyActive() && GuiTextFieldNop.getActive().id >= 0 && GuiTextFieldNop.getActive().id < 3) {
            selectedTargetField = GuiTextFieldNop.getActive();
            selectedTargetRow = selectedTargetField.id;
        }
        return handled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (quest.type == 6 && draggingRecipeScrollbar) {
            updateRecipeScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingRecipeScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateRecipeScrollFromMouse(double mouseY) {
        int maxOffset = Math.max(0, visibleRecipes.size() - 6);
        if (maxOffset == 0) return;
        int thumbHeight = Math.max(24, 162 * 6 / visibleRecipes.size());
        double fraction = Math.max(0, Math.min(1, (mouseY - guiTop - 126 - thumbHeight / 2.0) / (162 - thumbHeight)));
        recipeScrollOffset = (int)Math.round(fraction * maxOffset);
        refreshRecipeList();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        double delta = vertical != 0 ? vertical : horizontal;
        if (quest.type == 6 && mouseX >= guiLeft + 168 && mouseX < guiLeft + 422
                && mouseY >= guiTop + 126 && mouseY < guiTop + 288 && !visibleRecipes.isEmpty()) {
            recipeScrollOffset = Math.max(0, Math.min(Math.max(0, visibleRecipes.size() - 6),
                    recipeScrollOffset - (delta > 0 ? 1 : -1)));
            refreshRecipeList();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {
        int index = scroll.getSelectedIndex();
        if (scroll != candidatesScroll || index < 0 || index >= visibleCandidates.size()) return;
        Candidate candidate = visibleCandidates.get(index);
        int row = selectedTargetField != null ? selectedTargetField.id : selectedTargetRow;
        if (getTextField(row).getValue().isBlank()) {
            for (int i = 0; i < 3; i++) if (getTextField(i).getValue().isBlank()) { row = i; break; }
        }
        selectedTargetRow = row;
        selectedTargetField = getTextField(row);
        selectedTargetField.setValue(candidate.value());
        saveTargets();
        nearbyPreview = candidate.preview();
    }

    @Override public void scrollDoubleClicked(String selected, GuiCustomScrollNop scroll) {
        scrollClicked(0, 0, 0, scroll);
    }

    private void drawRecipePreview(GuiGraphics graphics, QuestCraftRecipe recipe, int mouseX, int mouseY) {
        int cell = 22;
        boolean brewing = recipe.station().equals("brewing");
        boolean smelting = recipe.station().equals("furnace") || recipe.station().equals("blast_furnace")
                || recipe.station().equals("smoker");
        int columns = recipe.columns(), rows = recipe.rows();
        int gridWidth = columns * cell, gridHeight = brewing ? cell * 2 + 14 : smelting ? cell * 2 + 8 : rows * cell;
        int outputCount = brewing ? 3 : 1;
        int outputWidth = outputCount == 3 ? outputCount * cell : 36;
        int arrowWidth = 14, gap = 6;
        int contentWidth = gridWidth + gap + arrowWidth + gap + outputWidth;
        int startX = guiLeft + 428 + Math.max(3, (166 - contentWidth) / 2);
        int previewHeight = Math.max(gridHeight, outputCount == 3 ? gridHeight : 36);
        int startY = guiTop + 126 + (132 - previewHeight) / 2;
        for (int i = 0; i < columns * rows; i++) {
            int x, y;
            if (brewing) {
                if (i == 1) {
                    x = startX + cell;
                    y = startY;
                } else if (i >= 3) {
                    int bottle = i - 3;
                    x = startX + bottle * cell;
                    y = startY + cell + 7 + (bottle == 1 ? 7 : 0);
                } else continue;
            } else if (smelting) {
                x = startX;
                y = startY + i * (cell + 8);
            } else {
                x = startX + i % columns * cell;
                y = startY + i / columns * cell;
            }
            graphics.fill(x, y, x + cell, y + cell, 0xff252a2d);
            graphics.fill(x + 1, y + 1, x + cell - 1, y + cell - 1, 0xff8b8b8b);
            ItemStack input = recipe.inputAt(i);
            if (!input.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x + cell / 2f, y + cell / 2f, 0);
                graphics.pose().scale(1.25f, 1.25f, 1f);
                graphics.renderItem(input, -8, -8);
                graphics.renderItemDecorations(font, input, -8, -8);
                graphics.pose().popPose();
                if (mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell)
                    graphics.renderTooltip(font, input, mouseX, mouseY);
            }
        }
        int arrowX = startX + gridWidth + gap;
        graphics.drawString(font, "→", arrowX + 2, startY + previewHeight / 2 - 4, 0xffffe79a, false);
        int outputX = arrowX + arrowWidth + gap;
        for (int i = 0; i < outputCount; i++) {
            int size = outputCount == 3 ? cell : 36;
            int x = outputX + i * cell;
            int y = outputCount == 3
                    ? startY + cell + 7 + (i == 1 ? 7 : 0)
                    : startY + (previewHeight - size) / 2;
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xffd7c47a);
            graphics.fill(x, y, x + size, y + size, 0xff555555);
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xff8b8b8b);
            ItemStack output = QuestCraft.previewStack(recipe.previewOutputAt(i),
                    ((QuestCraft)quest.questInterface).required[selectedCraftSlot]);
            if (!output.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x + size / 2f, y + size / 2f, 0);
                graphics.pose().scale(outputCount == 3 ? 1.25f : 1.4f, outputCount == 3 ? 1.25f : 1.4f, 1f);
                graphics.renderItem(output, -8, -8);
                graphics.renderItemDecorations(font, output, -8, -8);
                graphics.pose().popPose();
                if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size)
                    graphics.renderTooltip(font, output, mouseX, mouseY);
            }
        }
        int detailsY = smelting ? 250 : 267;
        String name = font.plainSubstrByWidth(recipe.name(), 156);
        graphics.drawString(font, name, guiLeft + 433, guiTop + detailsY, 0xffeeeeee, false);
        graphics.drawString(font, I18n.get("cnpcplus.quest.craft.recipe.identity", recipe.id()),
                guiLeft + 433, guiTop + detailsY + 12, 0xffc6c6c6, false);
        if (smelting) {
            ItemStack fuel = recipe.inputAt(1);
            String fuelText = fuel.isEmpty()
                    ? I18n.get(recipe.genericFuelAllowed()
                            ? "cnpcplus.quest.craft.recipe.fuel.unspecified"
                            : "cnpcplus.quest.craft.recipe.fuel.unavailable")
                    : I18n.get("cnpcplus.quest.craft.recipe.fuel.specified", fuel.getHoverName().getString());
            graphics.drawString(font, font.plainSubstrByWidth(fuelText, 156),
                    guiLeft + 433, guiTop + detailsY + 24, 0xffe2d2a0, false);
            String genericFuelText = I18n.get(recipe.genericFuelAllowed()
                    ? "cnpcplus.quest.craft.recipe.fuel.generic_allowed"
                    : "cnpcplus.quest.craft.recipe.fuel.generic_disabled");
            graphics.drawString(font, font.plainSubstrByWidth(genericFuelText, 156),
                    guiLeft + 433, guiTop + detailsY + 36, 0xffc6c6c6, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hasSubGui()) return;
        if (quest.type == 6) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.title"), guiLeft + 12, guiTop + 10, 0x202020, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.recipe.output"), guiLeft + 20, guiTop + 36, 0x555555, false);
            graphics.vLine(guiLeft + 152, guiTop + 31, guiTop + 298, 0xffa0a0a0);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.recipe.station"), guiLeft + 168, guiTop + 22, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.recipe.list"), guiLeft + 168, guiTop + 115, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.recipe.preview"), guiLeft + 428, guiTop + 115, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.selected", selectedCraftSlot + 1), guiLeft + 20, guiTop + 158, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.craft.recipe.required"), guiLeft + 20, guiTop + 222, 0x555555, false);
            graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.craft.overview.help"),
                    guiLeft + 20, guiTop + 182, 122, 0x686868);

            int previewBottom = selectedRecipe != null && (selectedRecipe.station().equals("furnace")
                    || selectedRecipe.station().equals("blast_furnace") || selectedRecipe.station().equals("smoker"))
                    ? 300 : 288;
            graphics.fill(guiLeft + 428, guiTop + 126, guiLeft + 594, guiTop + previewBottom, 0xff343b40);
            if (selectedRecipe == null) graphics.drawString(font, I18n.get("cnpcplus.quest.craft.unset"),
                    guiLeft + 438, guiTop + 145, 0xffc6c6c6, false);
            else drawRecipePreview(graphics, selectedRecipe, mouseX, mouseY);
            if (visibleRecipes.isEmpty()) graphics.drawWordWrap(font,
                    Component.translatable("cnpcplus.quest.craft.recipe.empty"), guiLeft + 170, guiTop + 145, 236, 0xff686868);
            int maxOffset = Math.max(0, visibleRecipes.size() - 6);
            graphics.fill(guiLeft + 416, guiTop + 126, guiLeft + 420, guiTop + 288, 0xff777777);
            int thumbHeight = maxOffset == 0 ? 162 : Math.max(24, 162 * 6 / visibleRecipes.size());
            int thumbY = guiTop + 126 + (maxOffset == 0 ? 0 : (162 - thumbHeight) * recipeScrollOffset / maxOffset);
            graphics.fill(guiLeft + 416, thumbY, guiLeft + 420, thumbY + thumbHeight, 0xff303030);
        } else if (quest.type == 2 || quest.type == 4) {
            graphics.drawString(font, I18n.get(quest.type == 2 ? "cnpcplus.quest.kill.title" : "cnpcplus.quest.area.title"),
                    guiLeft + 12, guiTop + 12, 0x202020, false);
            if (quest.type == 4) {
                graphics.drawString(font, I18n.get("cnpcplus.quest.area.bounds"), guiLeft + 22, guiTop + 24, 0x555555, false);
                drawCoordinateLabels(graphics, 42, 34);
                graphics.drawString(font, I18n.get("cnpcplus.quest.area.targets"), guiLeft + 18, guiTop + 112, 0x555555, false);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.name"), guiLeft + 18, guiTop + 128, 0x555555, false);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.amount"), guiLeft + imageWidth - 274, guiTop + 128, 0x555555, false);
            } else {
                graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.kill.help"),
                        guiLeft + 18, guiTop + 36, 330, 0x555555);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.name"), guiLeft + 18, guiTop + 66, 0x555555, false);
                graphics.drawString(font, I18n.get("cnpcplus.quest.targets.amount"), guiLeft + imageWidth - 274, guiTop + 66, 0x555555, false);
            }
            int px = guiLeft + imageWidth - 202, py = guiTop + (quest.type == 4 ? 184 : 168);
            graphics.drawString(font, I18n.get("cnpcplus.quest.targets.candidates"), px, guiTop + 24, 0x555555, false);
            graphics.fill(px - 1, py - 1, px + 190, py + 102, 0xffa49d89);
            graphics.fill(px, py, px + 188, py + 100, 0xff343b40);
            if (nearbyPreview != null) {
                graphics.enableScissor(px, py, px + 188, py + 100);
                SafeEntityPreview.nativeDisplay(graphics, nearbyPreview, 94, 94, 0.9f, 180,
                        mouseX, mouseY, px, py, true, false);
                graphics.disableScissor();
            } else {
                graphics.drawWordWrap(font, Component.translatable("cnpcplus.quest.targets.preview.help"),
                        px + 8, py + 24, 172, 0xffc6c6c6);
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
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.percent"), guiLeft + 22, guiTop + 128, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.distance"), guiLeft + 230, guiTop + 148, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.progress_text"), guiLeft + 22, guiTop + 176, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.coverage.label"), guiLeft + 22, guiTop + 214, 0x555555, false);
            graphics.drawString(font, I18n.get("cnpcplus.quest.explore.distance.help"), guiLeft + 22, guiTop + 254, 0x686868, false);
            if (Minecraft.getInstance().level != null) {
                QuestExplore q = (QuestExplore)quest.questInterface;
                long cells = q.cellCount(Minecraft.getInstance().level.getMinBuildHeight(), Minecraft.getInstance().level.getMaxBuildHeight());
                if (cells <= 0 || cells > QuestExplore.MAX_CELLS)
                    graphics.drawString(font, I18n.get("cnpcplus.quest.explore.invalid"), guiLeft + 22, guiTop + 270, 0xff3030, false);
                else if (cells == 1)
                    graphics.drawString(font, I18n.get("cnpcplus.quest.explore.small"), guiLeft + 22, guiTop + 270, 0xff9a6a18, false);
                else graphics.drawString(font, I18n.get("cnpcplus.quest.explore.cells", cells), guiLeft + 22, guiTop + 270, 0x686868, false);
            }
        }
    }

    private void drawCoordinateLabels(GuiGraphics graphics, int top, int rowGap) {
        String[] labels = {"X1", "Y1", "Z1", "X2", "Y2", "Z2"};
        int step = quest.type == 4 ? 82 : 108;
        for (int i = 0; i < 6; i++) {
            int x = guiLeft + 22 + (i % 3) * step;
            int y = guiTop + top + (i / 3) * rowGap;
            graphics.drawString(font, labels[i], x, y + 6, 0x303030, false);
        }
    }
}
