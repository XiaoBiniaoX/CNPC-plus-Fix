package bin.cnpcplus.client.gui;

import bin.cnpcplus.quest.CraftStation;
import bin.cnpcplus.quest.QuestAreaKill;
import bin.cnpcplus.quest.QuestCraft;
import bin.cnpcplus.quest.QuestExplore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import noppes.npcs.client.gui.util.ISliderListener;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.entity.player.EntityPlayer;
import bin.cnpcplus.text.client.PlayerTextPreview;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestKill;
import noppes.npcs.quests.QuestLocation;
import noppes.npcs.quests.QuestManual;

/** Edits quest objectives directly; the parent editor remains responsible for QuestSave. */
public final class SubGuiQuestSettings extends GuiQuestPanel implements ITextfieldListener, ICustomScrollListener, ISliderListener {
    private final Quest quest;
    private final int type;
    private final List<String> entityIds = new ArrayList<String>();
    private GuiCustomScroll entityScroll;
    private int selectedTarget;
    private final List<String> entityValues = new ArrayList<>();
    private final List<EntityLivingBase> entityPreviews = new ArrayList<>();
    private EntityLivingBase preview;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private String hoveredText;
    private String targetSearch = "", recipeSearch = "";
    private int craftSlot, catalogRevision = -1;
    private boolean draggingRecipeScrollbar;
    private CraftStation craftStation = CraftStation.WORKBENCH;
    private int recipeOffset;
    private final List<QuestCraftRecipeCatalog.Recipe> visibleRecipes = new ArrayList<>();
    private QuestCraftRecipeCatalog.Recipe selectedRecipe;
    private final List<String> visibleTargetKeys = new ArrayList<>();

    public SubGuiQuestSettings(Quest quest) {
        this.quest = quest;
        type = quest.type;
        xSize = type == 4 || type == 2 ? 560 : type == 6 ? 600 : type == 7 ? 380 : type == 5 ? 360 : 350;
        ySize = type == 4 ? 320 : type == 2 ? 300 : type == 6 ? 340 : type == 7 ? 320 : type == 5 ? 230 : 220;
        closeOnEsc = true;
        if (type == 6) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            for (int i = 0; i < 9; i++) if (craft.configured(i)) {
                craftSlot = i; craftStation = CraftStation.byKey(craft.stations[i]); break;
            }
            QuestCraftRecipeCatalog.request();
        }
    }

    @Override public void initGui() {
        super.initGui();
        draggingRecipeScrollbar = false;
        if (type == 2 || type == 4) setupKill();
        else if (type == 3) setupLocation();
        else if (type == 5) setupManual();
        else if (type == 6) setupCraft();
        else if (type == 7) setupExplore();
        addButton(new GuiNpcButton(23, guiLeft + xSize - 86, guiTop + ySize - (type == 3 ? 36 : 28), 74, 20, "gui.done"));
    }

    private void setupKill() {
        QuestKill kill = (QuestKill)quest.questInterface;
        visibleTargetKeys.clear();
        int rowY = guiTop + (type == 4 ? 148 : 82);
        for (int i = 0; i < 3; i++) {
            String name = "";
            int count = 1;
            int at = 0;
            for (Map.Entry<String, Integer> entry : kill.targets.entrySet()) {
                if (at++ == i) { name = entry.getKey(); count = entry.getValue(); break; }
            }
            addField(i, guiLeft + 18, rowY + i * 25, 244, name);
            if (!name.isEmpty()) visibleTargetKeys.add(name);
            addCountField(i + 3, guiLeft + 286, rowY + i * 25, 54, count, 1, Integer.MAX_VALUE);
        }
        if (type == 4) {
            QuestAreaKill area = (QuestAreaKill)quest.questInterface;
            addCoordinates(new int[]{area.x1, area.y1, area.z1, area.x2, area.y2, area.z2}, 30);
        }
        addField(70, guiLeft + 358, guiTop + 42, 188, targetSearch).setMaxStringLength(128);
        addEntityScroll(guiLeft + 358, guiTop + 66, 188, type == 4 ? 116 : 100);
    }

    private void setupLocation() {
        QuestLocation location = (QuestLocation)quest.questInterface;
        String[] values = {location.location, location.location2, location.location3};
        for (int i = 0; i < values.length; i++) {
            addField(i, guiLeft + 18, guiTop + 68 + i * 34, xSize - 36, values[i]);
        }
    }

    private void setupManual() {
        QuestManual manual = (QuestManual)quest.questInterface;
        visibleTargetKeys.clear();
        int i = 0;
        for (Map.Entry<String, Integer> entry : manual.manuals.entrySet()) {
            if (i >= 3) break;
            addField(i, guiLeft + 18, guiTop + 82 + i * 25, 212, entry.getKey());
            visibleTargetKeys.add(entry.getKey());
            addCountField(i + 3, guiLeft + 236, guiTop + 82 + i * 25, 54, entry.getValue(), 1, Integer.MAX_VALUE);
            i++;
        }
        while (i < 3) {
            addField(i, guiLeft + 18, guiTop + 82 + i * 25, 212, "");
            addCountField(i + 3, guiLeft + 236, guiTop + 82 + i * 25, 54, 1, 1, Integer.MAX_VALUE);
            i++;
        }
    }

    private void setupCraft() {
        QuestCraft craft = (QuestCraft)quest.questInterface;
        for (int i = 0; i < 9; i++)
            addButton(new GuiChoiceButton(30 + i, guiLeft + 20 + i % 3 * 32,
                    guiTop + 55 + i / 3 * 32, 20, 20, "", i == craftSlot));
        addCountField(50, guiLeft + 20, guiTop + 245, 112, Math.max(1, craft.requiredCount(craftSlot)), 1, 1000000);
        getTextField(50).enabled = craft.configured(craftSlot);
        addButton(new GuiNpcButton(51, guiLeft + 20, guiTop + 274, 112, 20, "gui.clear"));
        for (CraftStation station : CraftStation.values()) {
            int i = station.ordinal();
            addButton(new GuiChoiceButton(100 + i, guiLeft + 168 + i % 6 * 32,
                    guiTop + 38 + i / 6 * 28, 24, 24, "", station == craftStation));
        }
        addField(52, guiLeft + 168, guiTop + 96, 246, recipeSearch);
        for (int i = 0; i < 6; i++)
            addButton(new RecipeRowButton(300 + i, guiLeft + 168, guiTop + 126 + i * 27));
        for (int i = 0; i < 3; i++)
            addButton(new GuiChoiceButton(60 + i, 0, 0, 36, 36, "", false));
        filterRecipes();
    }

    private void setupExplore() {
        QuestExplore explore = (QuestExplore)quest.questInterface;
        addCoordinates(new int[]{explore.x1, explore.y1, explore.z1, explore.x2, explore.y2, explore.z2},
                0);
        addCountField(7, guiLeft + 278, guiTop + 142, 68, explore.viewDistance, 1, 128);
        GuiNpcSlider percent = new GuiNpcSlider(this, 72, guiLeft + 22, guiTop + 142, 190, 20, (explore.requiredPercent - 1) / 79F);
        percent.setString(explore.requiredPercent + "%");
        addSlider(percent);
        addField(8, guiLeft + 22, guiTop + 188, xSize - 44, explore.getProgressText()).setMaxStringLength(64);
        addButton(new GuiNpcButton(22, guiLeft + 22, guiTop + 228, 250, 20, explore.coverageModeKey()));
        addButton(new GuiNpcButton(24, guiLeft + 280, guiTop + 228, 78, 20, "cnpcplus.quest.explore.coverage.help"));
    }

    private void addCoordinates(int[] values, int id) {
        int step = type == 4 ? 82 : 108, width = type == 4 ? 50 : 72;
        for (int i = 0; i < 6; i++) {
            int column = i % 3;
            int row = i / 3;
            GuiNpcTextField field = addField(id + i, guiLeft + 48 + column * step, guiTop + 42 + row * 34, width, Integer.toString(values[i]));
            field.numbersOnly = true;
            field.setMinMaxDefault(-30000000, 30000000, 0);
            field.setMaxStringLength(11);
        }
    }

    private GuiNpcTextField addField(int id, int x, int y, int width, String value) {
        GuiNpcTextField field = new GuiNpcTextField(id, this, fontRenderer, x, y, width, 20, value);
        addTextField(field);
        return field;
    }

    private void addCountField(int id, int x, int y, int width, int value, int min, int max) {
        GuiNpcTextField field = addField(id, x, y, width, Integer.toString(value));
        field.numbersOnly = true;
        field.setMinMaxDefault(min, max, min);
    }

    private void addEntityScroll(int x, int y, int width, int height) {
        entityScroll = new GuiCustomScroll(this, 0);
        entityScroll.setSize(width, height);
        entityScroll.guiLeft = x;
        entityScroll.guiTop = y;
        entityIds.clear();
        entityValues.clear(); entityPreviews.clear();
        if (mc.world != null && mc.player != null) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            List<EntityLivingBase> nearby = new ArrayList<>();
            nearby.add(mc.player);
            nearby.addAll(mc.world.getEntitiesWithinAABB(EntityLivingBase.class, mc.player.getEntityBoundingBox().grow(128)));
            for (EntityLivingBase entity : nearby) {
                if (!entity.isEntityAlive() || entity.getDistanceSq(mc.player) > 128D * 128D) continue;
                boolean npc = entity instanceof EntityNPCInterface;
                String value = npc ? entity.getName() : entity instanceof EntityPlayer ? "Player" : EntityList.getEntityString(entity);
                if (value == null || value.trim().isEmpty() || !seen.add((npc ? "npc:" : "entity:") + value)) continue;
                String label = npc ? "NPC: " + value : entity instanceof EntityPlayer
                        ? I18n.translateToLocal("cnpcplus.quest.target.player") : entity.getName() + " [" + value + "]";
                if (!bin.cnpcplus.transport.client.Pinyin.matches(targetSearch, label + " " + value)) continue;
                entityIds.add(label); entityValues.add(value);
                entityPreviews.add(npc ? PlayerTextPreview.npc((EntityNPCInterface)entity) : entity);
            }
        }
        entityScroll.setUnsortedList(entityIds);
        addScroll(entityScroll);
    }

    private void saveTargets() {
        TreeMap<String, Integer> updated = new TreeMap<>(type == 5
                ? ((QuestManual)quest.questInterface).manuals : ((QuestKill)quest.questInterface).targets);
        updated.keySet().removeAll(visibleTargetKeys);
        visibleTargetKeys.clear();
        for (int i = 0; i < 3; i++) {
            String name = getTextField(i).getText().trim();
            if (!name.isEmpty()) {
                updated.put(name, Math.max(1, getTextField(i + 3).getInteger()));
                visibleTargetKeys.add(name);
            }
        }
        if (type == 5) ((QuestManual)quest.questInterface).manuals = updated;
        else if (type == 4) {
            TreeMap<String, Integer> targets = ((QuestAreaKill)quest.questInterface).targets;
            targets.clear();
            targets.putAll(updated);
        } else ((QuestKill)quest.questInterface).targets = updated;
    }

    private void saveRegion() {
        if (type == 4) {
            QuestAreaKill area = (QuestAreaKill)quest.questInterface;
            area.x1 = getTextField(30).getInteger(); area.y1 = getTextField(31).getInteger(); area.z1 = getTextField(32).getInteger();
            area.x2 = getTextField(33).getInteger(); area.y2 = getTextField(34).getInteger(); area.z2 = getTextField(35).getInteger();
            area.boundsSet = true;
        } else if (type == 7) {
            QuestExplore explore = (QuestExplore)quest.questInterface;
            explore.x1 = getTextField(0).getInteger(); explore.y1 = getTextField(1).getInteger(); explore.z1 = getTextField(2).getInteger();
            explore.x2 = getTextField(3).getInteger(); explore.y2 = getTextField(4).getInteger(); explore.z2 = getTextField(5).getInteger();
            explore.viewDistance = getTextField(7).getInteger();
            explore.boundsSet = true;
        }
    }

    @Override public void unFocused(GuiNpcTextField field) {
        int id = -1;
        for (int candidate = 0; candidate <= 70; candidate++) {
            if (getTextField(candidate) == field) { id = candidate; break; }
        }
        if (type == 2 || type == 4 || type == 5) {
            if (id >= 0 && id < 3) selectedTarget = id;
            if (id >= 0 && id < 6) saveTargets();
            if (type == 4 && id >= 30 && id < 36) saveRegion();
        } else if (type == 3 && id >= 0 && id < 3) {
            QuestLocation location = (QuestLocation)quest.questInterface;
            if (id == 0) location.location = field.getText().trim();
            else if (id == 1) location.location2 = field.getText().trim();
            else location.location3 = field.getText().trim();
        } else if (type == 6 && id == 50) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            craft.required[craftSlot] = field.getInteger();
        } else if (type == 7 && id >= 0 && id < 8) {
            saveRegion();
        } else if (type == 7 && id == 8) ((QuestExplore)quest.questInterface).setProgressText(field.getText());
    }

    @Override public void buttonEvent(GuiButton button) {
        if (button.id == 23) {
            close();
        } else if (type == 7 && button.id == 22) {
            GuiNpcTextField.unfocus();
            QuestExplore explore = (QuestExplore)quest.questInterface;
            explore.horizontalCoverage = !explore.horizontalCoverage;
            initGui();
        } else if (type == 7 && button.id == 24) {
            GuiNpcTextField.unfocus();
            setSubGui(new SubGuiQuestExploreCoverageHelp());
        } else if (type == 6) {
            GuiNpcTextField.unfocus();
            QuestCraft craft = (QuestCraft)quest.questInterface;
            if (button.id >= 30 && button.id < 39) {
                craftSlot = button.id - 30;
                CraftStation station = CraftStation.byKey(craft.stations[craftSlot]);
                if (station != null) craftStation = station;
                selectedRecipe = null; initGui();
            } else if (button.id >= 100 && button.id < 100 + CraftStation.values().length) {
                craftStation = CraftStation.values()[button.id - 100];
                recipeOffset = 0; initGui();
            } else if (button.id == 51) {
                craft.clearRecipe(craftSlot);
                selectedRecipe = null; initGui();
            } else if (button.id >= 300 && button.id < 306) {
                selectedRecipe = visibleRecipes.get(recipeOffset + button.id - 300);
                chooseOutput(selectedRecipe.outputs.get(0)); initGui();
            } else if (button.id >= 60 && button.id < 63 && selectedRecipe != null && button.id - 60 < selectedRecipe.previewOutputs.size()
                    && !selectedRecipe.previewOutputs.get(button.id - 60).isEmpty()) {
                chooseOutput(selectedRecipe.previewOutputs.get(button.id - 60)); initGui();
            }
        }
    }

    @Override public void save() {
        GuiNpcTextField.unfocus();
        if (type == 2 || type == 4 || type == 5) saveTargets();
        if (type == 4 || type == 7) {
            saveRegion();
            if (type == 4) ((QuestAreaKill)quest.questInterface).normalize();
            else ((QuestExplore)quest.questInterface).normalize();
        }
    }

    @Override public void scrollClicked(int mouseX, int mouseY, int button, GuiCustomScroll scroll) {
        if (scroll == entityScroll) {
            int index = scroll.selected;
            if (index >= 0 && index < entityIds.size()) {
                preview = entityPreviews.get(index);
                if (getTextField(selectedTarget).getText().trim().isEmpty())
                    for (int i = 0; i < 3; i++) if (getTextField(i).getText().trim().isEmpty()) { selectedTarget = i; break; }
                getTextField(selectedTarget).setText(entityValues.get(index));
                saveTargets();
            }
        }
    }

    @Override public void scrollDoubleClicked(String selection, GuiCustomScroll scroll) {
        scrollClicked(0, 0, 0, scroll);
    }

    private void chooseOutput(ItemStack output) {
        QuestCraft craft = (QuestCraft)quest.questInterface;
        craft.required[craftSlot] = getTextField(50).getInteger();
        craft.setRecipe(craftSlot, craftStation.key, selectedRecipe.id, selectedRecipe.name, output);
    }

    private void filterRecipes() {
        visibleRecipes.clear();
        selectedRecipe = null;
        QuestCraft craft = (QuestCraft)quest.questInterface;
        for (QuestCraftRecipeCatalog.Recipe recipe : QuestCraftRecipeCatalog.recipes(craftStation)) {
            if (craftStation.key.equals(craft.stations[craftSlot]) && recipe.id.equals(craft.recipeIds[craftSlot])
                    && (recipe.outputs.isEmpty() || recipe.outputs.stream().anyMatch(s -> QuestCraft.matches(craft.items.items.get(craftSlot), s)))) selectedRecipe = recipe;
            if (!recipe.outputs.isEmpty() && bin.cnpcplus.transport.client.Pinyin.matches(recipeSearch, recipe.name + " " + recipe.id
                    + " " + recipe.outputs.get(0).getDisplayName())) visibleRecipes.add(recipe);
        }
        recipeOffset = Math.max(0, Math.min(recipeOffset, Math.max(0, visibleRecipes.size() - 6)));
        for (int i = 0; i < 6; i++) {
            RecipeRowButton row = (RecipeRowButton)getButton(300 + i);
            row.recipe = recipeOffset + i < visibleRecipes.size() ? visibleRecipes.get(recipeOffset + i) : null;
            row.visible = row.recipe != null;
        }
        catalogRevision = QuestCraftRecipeCatalog.revision;
        int[] layout = selectedRecipe == null ? null : recipeLayout();
        for (int i = 0; i < 3; i++) {
            GuiNpcButton output = getButton(60 + i);
            output.visible = selectedRecipe != null && i < selectedRecipe.previewOutputs.size();
            output.enabled = output.visible && !selectedRecipe.previewOutputs.get(i).isEmpty();
            if (output.visible) {
                int size = layout[5] == 1 ? 36 : 22;
                output.width = output.height = size;
                output.x = layout[2] + layout[0] * 22 + 26 + i * 22;
                output.y = layout[3] + (craftStation == CraftStation.BREWING && layout[5] > 1
                        ? 29 + (i == 1 ? 7 : 0) : (layout[4] - size) / 2);
            }
        }
    }

    @Override public void keyTyped(char character, int key) throws java.io.IOException {
        if (hasSubGui()) { super.keyTyped(character, key); return; }
        super.keyTyped(character, key);
        if (type == 6) {
            String search = getTextField(52).getText();
            if (!search.equals(recipeSearch)) { recipeSearch = search; recipeOffset = 0; filterRecipes(); }
        } else if (type == 2 || type == 4) {
            String search = getTextField(70).getText();
            if (!search.equals(targetSearch)) { targetSearch = search; initGui(); getTextField(70).setFocused(true); }
        }
    }

    @Override public void updateScreen() {
        super.updateScreen();
        if (type == 6 && catalogRevision != QuestCraftRecipeCatalog.revision) filterRecipes();
    }

    @Override public void mouseDragged(GuiNpcSlider slider) {
        if (slider.id == 72) {
            int percent = 1 + Math.round(slider.sliderValue * 79);
            ((QuestExplore)quest.questInterface).requiredPercent = percent;
            slider.setString(percent + "%");
        }
    }
    @Override public void mousePressed(GuiNpcSlider slider) { mouseDragged(slider); }
    @Override public void mouseReleased(GuiNpcSlider slider) {}

    @Override public void mouseEvent(int x, int y, int button) {
        if (type == 2 || type == 4 || type == 5)
            for (int i = 0; i < 3; i++) if (getTextField(i).isFocused()) selectedTarget = i;
        if (type == 6 && button == 0 && x >= guiLeft + 416 && x < guiLeft + 422
                && y >= guiTop + 126 && y < guiTop + 288) draggingRecipeScrollbar = true;
    }

    @Override protected void drawPanelContents(int mouseX, int mouseY, float partialTicks) {
        hoveredStack = ItemStack.EMPTY;
        hoveredText = null;
        String title = type == 2 ? "cnpcplus.quest.kill.title" : type == 4 ? "cnpcplus.quest.area.title"
                : type == 3 ? "cnpcplus.quest.location.title" : type == 5 ? "cnpcplus.quest.manual.title"
                : type == 6 ? "cnpcplus.quest.craft.title" : "cnpcplus.quest.explore.title";
        fontRenderer.drawString(I18n.translateToLocal(title), guiLeft + 12, guiTop + 12, 0x202020);
        if (type == 2 || type == 4) {
            int labelsY = guiTop + (type == 4 ? 128 : 66);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.targets.name"), guiLeft + 18, labelsY, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.targets.amount"), guiLeft + 286, labelsY, 0x555555);
            if (type == 4) {
                fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.area.bounds"), guiLeft + 22, guiTop + 24, 0x555555);
                drawCoordinateLabels();
                fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.area.targets"), guiLeft + 18, guiTop + 112, 0x555555);
            } else fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.kill.help"), guiLeft + 18, guiTop + 36, 330, 0x555555);
            int px = guiLeft + 358, py = guiTop + (type == 4 ? 184 : 168);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.targets.candidates"), px, guiTop + 24, 0x555555);
            drawRect(px - 1, py - 1, px + 190, py + 102, 0xffa49d89);
            drawRect(px, py, px + 188, py + 100, 0xff343b40);
            if (preview != null) {
                double scaleX = mc.displayWidth / (double)width, scaleY = mc.displayHeight / (double)height;
                double left = (width - xSize * panelScale) / 2 + (px - guiLeft) * panelScale;
                double top = (height - ySize * panelScale) / 2 + (py - guiTop) * panelScale;
                int x1 = (int)Math.floor(left * scaleX), x2 = (int)Math.ceil((left + 188 * panelScale) * scaleX);
                int y1 = (int)Math.floor((height - top - 100 * panelScale) * scaleY), y2 = (int)Math.ceil((height - top) * scaleY);
                GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
                GL11.glScissor(x1, y1, x2 - x1, y2 - y1);
                try { SafeEntityPreview.draw(preview, px, py, 188, 100, .9F, 180); }
                finally { GL11.glPopAttrib(); }
            } else fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.targets.preview.help"), px + 8, py + 24, 172, 0xc6c6c6);
        } else if (type == 3) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.location.prompt"), guiLeft + 18, guiTop + 36, 0x303030);
            for (int i = 0; i < 3; i++) fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.location.marker") + " " + (i + 1),
                    guiLeft + 18, guiTop + 56 + i * 34, 0x303030);
        } else if (type == 5) {
            fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.manual.help"), guiLeft + 18, guiTop + 36, xSize - 36, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.manual.key"), guiLeft + 18, guiTop + 68, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.targets.amount"), guiLeft + 236, guiTop + 68, 0x555555);
        } else if (type == 6) {
            QuestCraft craft = (QuestCraft)quest.questInterface;
            drawRect(guiLeft + 152, guiTop + 31, guiLeft + 153, guiTop + 298, 0xffa0a0a0);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.targets"), guiLeft + 20, guiTop + 36, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.source.select"), guiLeft + 168, guiTop + 22, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.recipe.list"), guiLeft + 168, guiTop + 115, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.recipe.preview"), guiLeft + 428, guiTop + 115, 0x555555);
            for (int i = 0; i < 9; i++) {
                ItemStack stack = craft.items.items.get(i).copy();
                if (!stack.isEmpty()) stack.setCount(craft.requiredCount(i));
                drawStack(stack, guiLeft + 22 + i % 3 * 32, guiTop + 57 + i / 3 * 32, mouseX, mouseY);
            }
            fontRenderer.drawString(net.minecraft.client.resources.I18n.format("cnpcplus.quest.craft.slot", craftSlot + 1),
                    guiLeft + 20, guiTop + 158, 0x555555);
            fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.craft.hint"), guiLeft + 20, guiTop + 182, 122, 0x686868);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.count"), guiLeft + 20, guiTop + 222, 0x555555);
            for (CraftStation station : CraftStation.values()) {
                GuiNpcButton icon = getButton(100 + station.ordinal());
                drawStack(stationIcon(station), icon.x + 4, icon.y + 4, -1, -1);
                if (mouseX >= icon.x && mouseX < icon.x + 24 && mouseY >= icon.y && mouseY < icon.y + 24)
                    hoveredText = I18n.translateToLocal("cnpcplus.quest.source." + station.key);
            }
            int max = Math.max(0, visibleRecipes.size() - 6);
            int thumb = max == 0 ? 162 : Math.max(24, 162 * 6 / visibleRecipes.size());
            int next = recipeOffset;
            if (mouseX >= guiLeft + 168 && mouseX < guiLeft + 422 && mouseY >= guiTop + 126 && mouseY < guiTop + 288)
                next -= Integer.signum(Mouse.getDWheel());
            if (!Mouse.isButtonDown(0)) draggingRecipeScrollbar = false;
            if (draggingRecipeScrollbar && max > 0)
                next = Math.round((mouseY - guiTop - 126F - thumb / 2F) / (162 - thumb) * max);
            next = Math.max(0, Math.min(next, max));
            if (next != recipeOffset) { recipeOffset = next; filterRecipes(); }
            drawRect(guiLeft + 416, guiTop + 126, guiLeft + 420, guiTop + 288, 0xff777777);
            int thumbY = guiTop + 126 + (max == 0 ? 0 : (162 - thumb) * recipeOffset / max);
            drawRect(guiLeft + 416, thumbY, guiLeft + 420, thumbY + thumb, 0xff303030);
            if (visibleRecipes.isEmpty()) fontRenderer.drawSplitString(I18n.translateToLocal("cnpcplus.quest.recipe.empty"),
                    guiLeft + 172, guiTop + 142, 234, 0x686868);
            drawRect(guiLeft + 428, guiTop + 126, guiLeft + 594,
                    guiTop + (selectedRecipe != null && craftStation == CraftStation.FURNACE ? 300 : 288), 0xff343b40);
            if (selectedRecipe != null) {
                drawRecipePreview(mouseX, mouseY);
            } else {
                String name = craft.configured(craftSlot) ? craft.recipeNames[craftSlot] : I18n.translateToLocal("cnpcplus.quest.target.empty");
                fontRenderer.drawSplitString(name, guiLeft + 438, guiTop + 142, 146, 0xc6c6c6);
            }
        } else if (type == 7) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.bounds"), guiLeft + 22, guiTop + 26, 0x555555);
            drawCoordinateLabels();
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.percent"), guiLeft + 22, guiTop + 128, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.distance.short"), guiLeft + 230, guiTop + 148, 0x555555);
            long cells = ((QuestExplore)quest.questInterface).cellCount();
            String hint = cells <= 0 ? "cnpcplus.quest.explore.invalid" : cells == 1 ? "cnpcplus.quest.explore.small" : "cnpcplus.quest.explore.cells";
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.text"), guiLeft + 22, guiTop + 176, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.coverage.label"), guiLeft + 22, guiTop + 214, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.distance.help"), guiLeft + 22, guiTop + 254, 0x686868);
            fontRenderer.drawString(net.minecraft.client.resources.I18n.format(hint, cells), guiLeft + 22, guiTop + 270, cells <= 1 ? 0xAA3333 : 0x686868);
        }
    }

    private int[] recipeLayout() {
        int columns = Math.max(1, selectedRecipe.columns);
        int rows = (selectedRecipe.inputs.size() + columns - 1) / columns;
        int count = selectedRecipe.previewOutputs.size();
        int gridHeight = craftStation == CraftStation.BREWING && rows > 0 ? 58 : craftStation == CraftStation.FURNACE ? rows * 30 : rows * 22;
        int outputWidth = count == 1 ? 36 : count * 22;
        int startX = guiLeft + 428 + Math.max(3, (166 - columns * 22 - 26 - outputWidth) / 2);
        int height = Math.max(gridHeight, count == 1 ? 36 : 58);
        return new int[]{columns, rows, startX, guiTop + 126 + (132 - height) / 2, height, count};
    }

    private void drawRecipePreview(int mouseX, int mouseY) {
        int[] layout = recipeLayout();
        int columns = layout[0], startX = layout[2], startY = layout[3];
        for (int i = 0; i < selectedRecipe.inputs.size(); i++) {
            int x = startX + i % columns * 22, y = startY + i / columns * 22;
            if (craftStation == CraftStation.BREWING) {
                x = startX + (i == 3 ? 1 : i) * 22;
                y = startY + (i == 3 ? 0 : 29 + (i == 1 ? 7 : 0));
            } else if (craftStation == CraftStation.FURNACE) y = startY + i * 30;
            drawRecipeStack(selectedRecipe.inputs.get(i), x, y, 22, mouseX, mouseY);
        }
        fontRenderer.drawString("→", startX + columns * 22 + 8, startY + layout[4] / 2 - 4, 0xffe79a);
        QuestCraft craft = (QuestCraft)quest.questInterface;
        for (int i = 0; i < selectedRecipe.previewOutputs.size(); i++) {
            GuiNpcButton output = getButton(60 + i);
            drawRect(output.x - 1, output.y - 1, output.x + output.width + 1, output.y + output.height + 1, 0xffd7c47a);
            ItemStack stack = selectedRecipe.previewOutputs.get(i).copy();
            stack.setCount(Math.max(1, craft.requiredCount(craftSlot)));
            drawRecipeStack(stack, output.x, output.y, output.width, mouseX, mouseY);
        }
        int detailsY = craftStation == CraftStation.FURNACE ? 250 : 267;
        fontRenderer.drawString(fontRenderer.trimStringToWidth(selectedRecipe.name, 156), guiLeft + 433, guiTop + detailsY, 0xeeeeee);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(net.minecraft.client.resources.I18n.format(
                "cnpcplus.quest.craft.recipe.identity", selectedRecipe.id), 156), guiLeft + 433, guiTop + detailsY + 12, 0xc6c6c6);
        if (craftStation == CraftStation.FURNACE) {
            ItemStack fuel = selectedRecipe.inputs.get(1);
            String fuelText = fuel.isEmpty() ? I18n.translateToLocal(selectedRecipe.genericFuelAllowed
                    ? "cnpcplus.quest.craft.recipe.fuel.unspecified" : "cnpcplus.quest.craft.recipe.fuel.unavailable")
                    : net.minecraft.client.resources.I18n.format("cnpcplus.quest.craft.recipe.fuel.specified", fuel.getDisplayName());
            fontRenderer.drawString(fontRenderer.trimStringToWidth(fuelText, 156), guiLeft + 433, guiTop + detailsY + 24, 0xe2d2a0);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(I18n.translateToLocal(selectedRecipe.genericFuelAllowed
                    ? "cnpcplus.quest.craft.recipe.fuel.generic_allowed" : "cnpcplus.quest.craft.recipe.fuel.generic_disabled"), 156),
                    guiLeft + 433, guiTop + detailsY + 36, 0xc6c6c6);
        }
    }

    private void drawRecipeStack(ItemStack stack, int x, int y, int size, int mouseX, int mouseY) {
        drawRect(x, y, x + size, y + size, 0xff252a2d);
        drawRect(x + 1, y + 1, x + size - 1, y + size - 1, 0xff8b8b8b);
        if (stack.isEmpty()) return;
        float scale = size == 36 ? 1.4F : 1.25F;
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(x + size / 2F, y + size / 2F, 0);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1);
        drawStack(stack, -8, -8, -100, -100);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
        if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) hoveredStack = stack;
    }

    private ItemStack stationIcon(CraftStation station) {
        switch (station) {
            case WORKBENCH: return new ItemStack(Blocks.CRAFTING_TABLE);
            case CARPENTRY: return new ItemStack(noppes.npcs.CustomItems.carpentyBench);
            case ANVIL: return new ItemStack(Blocks.ANVIL);
            case SMITHING: return new ItemStack(bin.cnpcplus.crafting.SmithingTable.TABLE);
            case BREWING: return new ItemStack(Items.BREWING_STAND);
            case FURNACE: return new ItemStack(Blocks.FURNACE);
            case CAMPFIRE: return new ItemStack(bin.cnpcplus.station.StationBlocks.CAMPFIRE);
            case GRINDSTONE: return new ItemStack(bin.cnpcplus.station.StationBlocks.GRINDSTONE);
            default: return new ItemStack(Blocks.DROPPER);
        }
    }

    private final class RecipeRowButton extends GuiNpcButton {
        private QuestCraftRecipeCatalog.Recipe recipe;
        RecipeRowButton(int id, int x, int y) { super(id, x, y, 246, 24, ""); }
        @Override public void drawButton(Minecraft mc, int mx, int my, float ticks) {
            if (!visible || recipe == null) return;
            boolean hover = mx >= x && mx < x + width && my >= y && my < y + height;
            drawRect(x, y, x + width, y + height, recipe == selectedRecipe ? 0xff373737 : 0xff777777);
            drawRect(x + 1, y + 1, x + width - 1, y + height - 1, hover ? 0xffd8d8d8 : 0xffc0c0c0);
            drawStack(recipe.outputs.get(0), x + 4, y + 4, -1, -1);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(recipe.name, width - 60), x + 26, y + 8, 0x202020);
            fontRenderer.drawString("×" + recipe.outputs.get(0).getCount(), x + width - 30, y + 8, 0x404040);
        }
    }

    private void drawStack(ItemStack stack, int x, int y, int mouseX, int mouseY) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF555555);
        drawRect(x, y, x + 16, y + 16, 0xFF999999);
        if (stack.isEmpty()) return;
        net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, null);
        net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) hoveredStack = stack;
    }

    @Override protected void drawPanelOverlay(int mouseX, int mouseY, float partialTicks) {
        if (!hoveredStack.isEmpty()) renderToolTip(hoveredStack, mouseX, mouseY);
        else if (hoveredText != null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(guiLeft, guiTop, 0);
            GuiUtils.drawHoveringText(Collections.singletonList(hoveredText), mouseX - guiLeft,
                    getButton(100).y - guiTop - 4, xSize, ySize, -1, fontRenderer);
            GlStateManager.popMatrix();
        }
    }

    private void drawCoordinateLabels() {
        String[] labels = {"X1", "Y1", "Z1", "X2", "Y2", "Z2"};
        int step = type == 4 ? 82 : 108;
        for (int i = 0; i < 6; i++) fontRenderer.drawString(labels[i], guiLeft + 22 + i % 3 * step,
                guiTop + 48 + i / 3 * 34, 0x303030);
    }
}
