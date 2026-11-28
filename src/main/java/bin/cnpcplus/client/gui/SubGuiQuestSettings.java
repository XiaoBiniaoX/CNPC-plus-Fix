package bin.cnpcplus.client.gui;

import bin.cnpcplus.quest.CraftStation;
import bin.cnpcplus.quest.QuestAreaKill;
import bin.cnpcplus.quest.QuestCraft;
import bin.cnpcplus.quest.QuestExplore;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import noppes.npcs.client.gui.util.ISliderListener;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestKill;
import noppes.npcs.quests.QuestLocation;
import noppes.npcs.quests.QuestManual;

/** Edits quest objectives directly; the parent editor remains responsible for QuestSave. */
public final class SubGuiQuestSettings extends SubGuiInterface implements ITextfieldListener, ICustomScrollListener, ISliderListener {
    private final Quest quest;
    private final int type;
    private final List<String> entityIds = new ArrayList<String>();
    private GuiCustomScroll entityScroll;
    private int selectedTarget;
    private final List<ResourceLocation> entityTypes = new ArrayList<>();
    private final List<EntityLivingBase> entityPreviews = new ArrayList<>();
    private EntityLivingBase preview;
    private boolean npcTargets;
    private String targetSearch = "", recipeSearch = "";
    private int rotation = 150, craftSlot, catalogRevision = -1;
    private CraftStation craftStation = CraftStation.WORKBENCH;
    private GuiCustomScroll recipeScroll;
    private final List<QuestCraftRecipeCatalog.Recipe> visibleRecipes = new ArrayList<>();
    private QuestCraftRecipeCatalog.Recipe selectedRecipe;

    public SubGuiQuestSettings(Quest quest) {
        this.quest = quest;
        type = quest.type;
        xSize = type == 4 || type == 2 ? 560 : type == 6 ? 600 : type == 7 ? 380 : type == 5 ? 360 : 350;
        ySize = type == 4 ? 320 : type == 2 ? 300 : type == 6 ? 340 : type == 7 ? 380 : type == 5 ? 230 : 220;
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
        if (type == 2 || type == 4) setupKill();
        else if (type == 3) setupLocation();
        else if (type == 5) setupManual();
        else if (type == 6) setupCraft();
        else if (type == 7) setupExplore();
        addButton(new GuiNpcButton(23, guiLeft + xSize - 110, guiTop + ySize - 28, 98, 20, "gui.back"));
    }

    private void setupKill() {
        QuestKill kill = (QuestKill)quest.questInterface;
        int rowY = guiTop + 54;
        for (int i = 0; i < 3; i++) {
            String name = "";
            int count = 1;
            int at = 0;
            for (Map.Entry<String, Integer> entry : kill.targets.entrySet()) {
                if (at++ == i) { name = entry.getKey(); count = entry.getValue(); break; }
            }
            addField(i, guiLeft + 12, rowY + i * 24, 142, name);
            addCountField(i + 3, guiLeft + 158, rowY + i * 24, 42, count, 1, 1000000);
            addButton(new GuiNpcButton(20 + i, guiLeft + 204, rowY + i * 24, 30, 20, "..."));
        }
        if (type == 4) {
            QuestAreaKill area = (QuestAreaKill)quest.questInterface;
            addCoordinates(new int[]{area.x1, area.y1, area.z1, area.x2, area.y2, area.z2}, 30, guiLeft + 12, guiTop + 150, 54);
            addButton(new GuiNpcButton(10, guiLeft + 12, guiTop + 204, 102, 20, "cnpcplus.quest.corner.a"));
            addButton(new GuiNpcButton(11, guiLeft + 120, guiTop + 204, 102, 20, "cnpcplus.quest.corner.b"));
        }
        addButton(new GuiNpcButton(24, guiLeft + 252, guiTop + 30, 100, 20,
                npcTargets ? "cnpcplus.quest.target.npc" : "cnpcplus.quest.entity.pick"));
        addField(70, guiLeft + 358, guiTop + 30, 186, targetSearch);
        addEntityScroll(guiLeft + 252, guiTop + 56, 160, ySize - 98);
        addSlider(new GuiNpcSlider(this, 71, guiLeft + 424, guiTop + ySize - 70, 120, 20, rotation / 360F));
    }

    private void setupLocation() {
        QuestLocation location = (QuestLocation)quest.questInterface;
        String[] values = {location.location, location.location2, location.location3};
        for (int i = 0; i < values.length; i++) {
            addField(i, guiLeft + 12, guiTop + 58 + i * 28, xSize - 24, values[i]);
        }
    }

    private void setupManual() {
        QuestManual manual = (QuestManual)quest.questInterface;
        int i = 0;
        for (Map.Entry<String, Integer> entry : manual.manuals.entrySet()) {
            if (i >= 3) break;
            addField(i, guiLeft + 12, guiTop + 54 + i * 26, 220, entry.getKey());
            addCountField(i + 3, guiLeft + 238, guiTop + 54 + i * 26, 70, entry.getValue(), 1, 1000000);
            i++;
        }
        while (i < 3) {
            addField(i, guiLeft + 12, guiTop + 54 + i * 26, 220, "");
            addCountField(i + 3, guiLeft + 238, guiTop + 54 + i * 26, 70, 1, 1, 1000000);
            i++;
        }
    }

    private void setupCraft() {
        QuestCraft craft = (QuestCraft)quest.questInterface;
        for (int i = 0; i < 9; i++) {
            GuiNpcButton slot = new GuiChoiceButton(30 + i, guiLeft + 20 + i % 3 * 32,
                    guiTop + 55 + i / 3 * 32, 28, 28, "", i == craftSlot);
            addButton(slot);
        }
        ItemStack target = craft.items.items.get(craftSlot);
        addCountField(50, guiLeft + 20, guiTop + 174, 88, target.isEmpty() ? 1 : craft.requiredCount(craftSlot), 1, 1000000);
        addButton(new GuiNpcButton(51, guiLeft + 20, guiTop + 204, 88, 20, "gui.clear"));
        for (CraftStation station : CraftStation.values()) {
            int i = station.ordinal();
            addButton(new GuiChoiceButton(100 + i, guiLeft + 140 + i % 5 * 88,
                    guiTop + 38 + i / 5 * 24, 84, 20, "cnpcplus.quest.source." + station.key, station == craftStation));
        }
        addField(52, guiLeft + 140, guiTop + 94, 300, recipeSearch);
        recipeScroll = new GuiCustomScroll(this, 1);
        recipeScroll.setSize(300, 160); recipeScroll.guiLeft = guiLeft + 140; recipeScroll.guiTop = guiTop + 120;
        addScroll(recipeScroll);
        for (int i = 0; i < 3; i++)
            addButton(new GuiNpcButton(60 + i, guiLeft + 460 + i * 38, guiTop + 250, 32, 24, ""));
        filterRecipes();
    }

    private void setupExplore() {
        QuestExplore explore = (QuestExplore)quest.questInterface;
        addCoordinates(new int[]{explore.x1, explore.y1, explore.z1, explore.x2, explore.y2, explore.z2},
                0, guiLeft + 12, guiTop + 64, 98);
        addButton(new GuiNpcButton(10, guiLeft + 12, guiTop + 144, 112, 20, "cnpcplus.quest.corner.a"));
        addButton(new GuiNpcButton(11, guiLeft + 132, guiTop + 144, 112, 20, "cnpcplus.quest.corner.b"));
        addCountField(6, guiLeft + 12, guiTop + 192, 64, explore.requiredPercent, 1, 80);
        addCountField(7, guiLeft + 154, guiTop + 192, 64, explore.viewDistance, 1, 128);
        addSlider(new GuiNpcSlider(this, 72, guiLeft + 12, guiTop + 218, 240, 20, (explore.requiredPercent - 1) / 79F));
        addField(8, guiLeft + 12, guiTop + 256, xSize - 24, explore.getProgressText()).setMaxStringLength(64);
        addButton(new GuiNpcButton(22, guiLeft + 12, guiTop + 284, 250, 20, explore.coverageModeKey()));
        addButton(new GuiNpcButton(24, guiLeft + 270, guiTop + 284, 98, 20, "cnpcplus.quest.explore.coverage.help"));
    }

    private void addCoordinates(int[] values, int id, int x, int y, int width) {
        for (int i = 0; i < 6; i++) {
            int column = i % 3;
            int row = i / 3;
            GuiNpcTextField field = addField(id + i, x + column * (width + 8), y + row * 28, width, Integer.toString(values[i]));
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
        entityTypes.clear(); entityPreviews.clear();
        if (npcTargets) {
            for (Entity entity : mc.world.loadedEntityList) if (entity instanceof EntityNPCInterface
                    && bin.cnpcplus.transport.client.Pinyin.matches(targetSearch, entity.getName())) {
                entityIds.add(entity.getName() + " [" + entity.getEntityId() + "]");
                entityTypes.add(null); entityPreviews.add((EntityLivingBase)entity);
            }
        } else for (EntityEntry entry : ForgeRegistries.ENTITIES.getValues()) {
            Class<? extends Entity> entityClass = entry.getEntityClass();
            try {
                if (EntityLivingBase.class.isAssignableFrom(entityClass)
                        && entityClass.getConstructor(World.class) != null
                        && !Modifier.isAbstract(entityClass.getModifiers())) {
                    String label = I18n.translateToLocal("entity." + entry.getName() + ".name") + " [" + entry.getRegistryName() + "]";
                    if (!bin.cnpcplus.transport.client.Pinyin.matches(targetSearch, label)) continue;
                    entityIds.add(label); entityTypes.add(entry.getRegistryName()); entityPreviews.add(null);
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        entityScroll.setUnsortedList(entityIds);
        addScroll(entityScroll);
    }

    private void saveTargets() {
        TreeMap<String, Integer> updated = new TreeMap<String, Integer>();
        for (int i = 0; i < 3; i++) {
            String name = getTextField(i).getText().trim();
            if (!name.isEmpty()) updated.put(name, Math.max(1, getTextField(i + 3).getInteger()));
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
            explore.requiredPercent = getTextField(6).getInteger(); explore.viewDistance = getTextField(7).getInteger();
            explore.boundsSet = true;
        }
    }

    private void setCorner(int corner) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        BlockPos pos = mc.player.getPosition();
        int base = type == 4 ? 30 + corner * 3 : corner * 3;
        getTextField(base).setText(Integer.toString(pos.getX()));
        getTextField(base + 1).setText(Integer.toString(pos.getY()));
        getTextField(base + 2).setText(Integer.toString(pos.getZ()));
        saveRegion();
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
            if (id == 6) {
                GuiNpcSlider slider = getSlider(72);
                slider.sliderValue = (((QuestExplore)quest.questInterface).requiredPercent - 1) / 79F;
                slider.setString(getTextField(6).getText() + "%");
            }
        } else if (type == 7 && id == 8) ((QuestExplore)quest.questInterface).setProgressText(field.getText());
    }

    @Override public void buttonEvent(GuiButton button) {
        if (button.id == 23) {
            close();
        } else if ((type == 4 || type == 7) && (button.id == 10 || button.id == 11)) {
            setCorner(button.id - 10);
        } else if (type == 7 && button.id == 22) {
            GuiNpcTextField.unfocus();
            QuestExplore explore = (QuestExplore)quest.questInterface;
            explore.horizontalCoverage = !explore.horizontalCoverage;
            initGui();
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
                selectedRecipe = null; recipeSearch = ""; initGui();
            } else if (button.id == 51) {
                craft.items.items.set(craftSlot, ItemStack.EMPTY); craft.stations[craftSlot] = ""; craft.recipeIds[craftSlot] = "";
                craft.required[craftSlot] = 0;
                selectedRecipe = null; initGui();
            } else if (button.id >= 60 && button.id < 63 && selectedRecipe != null && button.id - 60 < selectedRecipe.outputs.size()) {
                chooseOutput(selectedRecipe.outputs.get(button.id - 60)); initGui();
            }
        } else if ((type == 2 || type == 4) && button.id >= 20 && button.id < 23) {
            selectedTarget = button.id - 20;
        } else if ((type == 2 || type == 4) && button.id == 24) {
            npcTargets = !npcTargets; preview = null; initGui();
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
                if (preview == null) {
                    Entity entity = EntityList.createEntityByIDFromName(entityTypes.get(index), mc.world);
                    preview = entity instanceof EntityLivingBase ? (EntityLivingBase)entity : null;
                }
                if (preview == null) return;
                getTextField(selectedTarget).setText(npcTargets ? preview.getName() : EntityList.getEntityString(preview));
                saveTargets();
            }
        } else if (scroll == recipeScroll && scroll.selected >= 0 && scroll.selected < visibleRecipes.size()) {
            selectedRecipe = visibleRecipes.get(scroll.selected);
            if (!selectedRecipe.outputs.isEmpty()) chooseOutput(selectedRecipe.outputs.get(0));
            else ((QuestCraft)quest.questInterface).setRecipe(craftSlot, craftStation.key, selectedRecipe.id);
            initGui();
        }
    }

    @Override public void scrollDoubleClicked(String selection, GuiCustomScroll scroll) {
        scrollClicked(0, 0, 0, scroll);
    }

    private void chooseOutput(ItemStack output) {
        QuestCraft craft = (QuestCraft)quest.questInterface;
        ItemStack target = output.copy(); target.setCount(1);
        craft.items.items.set(craftSlot, target);
        craft.required[craftSlot] = getTextField(50).getInteger();
        craft.setRecipe(craftSlot, craftStation.key, selectedRecipe.id);
    }

    private void filterRecipes() {
        visibleRecipes.clear(); List<String> labels = new ArrayList<>();
        selectedRecipe = null;
        QuestCraft craft = (QuestCraft)quest.questInterface;
        for (QuestCraftRecipeCatalog.Recipe recipe : QuestCraftRecipeCatalog.recipes(craftStation)) {
            if (!bin.cnpcplus.transport.client.Pinyin.matches(recipeSearch, recipe.name + " " + recipe.id)) continue;
            visibleRecipes.add(recipe); labels.add(recipe.name + " [" + recipe.id + "]");
            if (craftStation.key.equals(craft.stations[craftSlot]) && recipe.id.equals(craft.recipeIds[craftSlot])
                    && (recipe.outputs.isEmpty() || recipe.outputs.stream().anyMatch(s -> QuestCraft.matches(craft.items.items.get(craftSlot), s)))) selectedRecipe = recipe;
        }
        recipeScroll.setUnsortedList(labels); recipeScroll.selected = visibleRecipes.indexOf(selectedRecipe);
        catalogRevision = QuestCraftRecipeCatalog.revision;
        for (int i = 0; i < 3; i++) getButton(60 + i).visible = selectedRecipe != null && i < selectedRecipe.outputs.size();
    }

    @Override public void keyTyped(char character, int key) throws java.io.IOException {
        super.keyTyped(character, key);
        if (type == 6) {
            String search = getTextField(52).getText();
            if (!search.equals(recipeSearch)) { recipeSearch = search; filterRecipes(); }
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
        if (slider.id == 71) rotation = Math.round(slider.sliderValue * 360);
        else if (slider.id == 72) {
            int percent = 1 + Math.round(slider.sliderValue * 79);
            getTextField(6).setText(Integer.toString(percent));
            ((QuestExplore)quest.questInterface).requiredPercent = percent;
            slider.setString(percent + "%");
        }
    }
    @Override public void mousePressed(GuiNpcSlider slider) { mouseDragged(slider); }
    @Override public void mouseReleased(GuiNpcSlider slider) {}

    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Tile the 256x217 panel inside menubg's 256x256 PNG; its remaining rows are transparent.
        GuiUtils.drawContinuousTexturedBox(getResource("menubg.png"), guiLeft, guiTop,
                0, 0, xSize, ySize, 256, 217, 4, zLevel);
        super.drawScreen(mouseX, mouseY, partialTicks);
        String title = type == 2 ? "cnpcplus.quest.kill.title" : type == 4 ? "cnpcplus.quest.area.title"
                : type == 3 ? "cnpcplus.quest.location.title" : type == 5 ? "cnpcplus.quest.manual.title"
                : type == 6 ? "cnpcplus.quest.craft.title" : "cnpcplus.quest.explore.title";
        fontRenderer.drawString(I18n.translateToLocal(title), guiLeft + 12, guiTop + 12, 0x202020);
        if (type == 2 || type == 4) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.target"), guiLeft + 12, guiTop + 40, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.count"), guiLeft + 158, guiTop + 40, 0x555555);
            if (type == 4) {
                fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.area.bounds"), guiLeft + 12, guiTop + 132, 0x555555);
                drawCoordinateLabels(guiLeft + 12, guiTop + 138, 62);
            }
            if (preview != null) {
                drawNpc(preview, 484, ySize - 80, Math.min(1.2F, 1.8F / Math.max(.5F, preview.height)), rotation);
                fontRenderer.drawString(fontRenderer.trimStringToWidth(preview.getName(), 120), guiLeft + 424, guiTop + 66, 0x303030);
            }
        } else if (type == 3) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.location.hint"), guiLeft + 12, guiTop + 38, 0x555555);
        } else if (type == 5) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.manual.hint"), guiLeft + 12, guiTop + 36, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.count"), guiLeft + 238, guiTop + 40, 0x555555);
        } else if (type == 6) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.craft.hint"), guiLeft + 12, guiTop + 26, 0x555555);
            for (int i = 0; i < 9; i++) {
                ItemStack stack = ((QuestCraft)quest.questInterface).items.items.get(i);
                drawStack(stack, guiLeft + 26 + i % 3 * 32, guiTop + 61 + i / 3 * 32, mouseX, mouseY);
            }
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.count"), guiLeft + 20, guiTop + 162, 0x555555);
            if (selectedRecipe != null) {
                fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.recipe.preview"), guiLeft + 460, guiTop + 94, 0x555555);
                int columns = Math.max(1, selectedRecipe.columns);
                for (int i = 0; i < selectedRecipe.inputs.size(); i++)
                    drawStack(selectedRecipe.inputs.get(i), guiLeft + 460 + i % columns * 24,
                            guiTop + 122 + i / columns * 24, mouseX, mouseY);
                for (int i = 0; i < selectedRecipe.outputs.size(); i++)
                    drawStack(selectedRecipe.outputs.get(i), guiLeft + 468 + i * 38, guiTop + 254, mouseX, mouseY);
                fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.recipe.output"), guiLeft + 460, guiTop + 234, 0x555555);
            }
            QuestCraft craft = (QuestCraft)quest.questInterface;
            String summary = craft.stations[craftSlot] + " / " + craft.recipeIds[craftSlot];
            fontRenderer.drawString(fontRenderer.trimStringToWidth(summary, 430), guiLeft + 140, guiTop + 286, 0x555555);
        } else if (type == 7) {
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.bounds"), guiLeft + 12, guiTop + 42, 0x555555);
            drawCoordinateLabels(guiLeft + 12, guiTop + 52, 106);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.percent"), guiLeft + 12, guiTop + 180, 0x555555);
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.distance"), guiLeft + 154, guiTop + 180, 0x555555);
            long cells = ((QuestExplore)quest.questInterface).cellCount();
            String hint = cells <= 0 ? "cnpcplus.quest.explore.invalid" : cells == 1 ? "cnpcplus.quest.explore.small" : "cnpcplus.quest.explore.cells";
            fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.text"), guiLeft + 12, guiTop + 244, 0x555555);
            fontRenderer.drawString(net.minecraft.client.resources.I18n.format(hint, cells), guiLeft + 12, guiTop + 316, cells <= 1 ? 0xAA3333 : 0x555555);
            QuestExplore explore = (QuestExplore)quest.questInterface;
            if (explore.y1 < 0 || explore.y2 > 255)
                fontRenderer.drawString(I18n.translateToLocal("cnpcplus.quest.explore.height"), guiLeft + 12, guiTop + 328, 0xAA3333);
            if (mouseX >= guiLeft + 270 && mouseX < guiLeft + 368 && mouseY >= guiTop + 284 && mouseY < guiTop + 304)
                drawHoveringText(fontRenderer.listFormattedStringToWidth(I18n.translateToLocal("cnpcplus.quest.explore.coverage.description"), 250), mouseX, mouseY);
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
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) renderToolTip(stack, mouseX, mouseY);
    }

    private void drawCoordinateLabels(int x, int y, int spacing) {
        fontRenderer.drawString("X", x, y, 0x686868);
        fontRenderer.drawString("Y", x + spacing, y, 0x686868);
        fontRenderer.drawString("Z", x + spacing * 2, y, 0x686868);
    }
}
