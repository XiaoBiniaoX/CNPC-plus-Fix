package top.cnpcplus.station;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import top.cnpcplus.smelting.client.GuiIconToggleButton;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.shared.client.gui.components.*;
import noppes.npcs.shared.client.gui.listeners.*;

/** Reuses the same CNPC container, scroll, text fields and availability subpanel as existing editors. */
public final class StationScreen extends GuiContainerNPCInterface2<StationMenu> implements ICustomScrollListener {
    private final StationMenu container;
    private StationRecipe editing = new StationRecipe();
    private GuiCustomScrollNop list;
    private String notice = "";
    private int result;
    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(NoppesUtil.getLastNpc(), menu, inventory, title);
        container = menu;
        imageWidth = 420; imageHeight = 250; menuYOffset = 0;
        drawDefaultBackground = false;
        setBackground("inventorymenu.png");
    }
    @Override public void m_7856_() {
        super.m_7856_();
        // CNPC and vanilla use different origin fields. Recompute, never accumulate offsets.
        guiLeft = (width - imageWidth) / 2;
        guiTop = (height - imageHeight) / 2 + 28;
        leftPos = guiLeft; topPos = guiTop;
        try {
            var field = GuiContainerNPCInterface2.class.getDeclaredField("menu");
            field.setAccessible(true);
            var menu = field.get(this);
            menu.getClass().getMethod("initGui", int.class, int.class, int.class).invoke(menu, guiLeft, guiTop + menuYOffset, imageWidth);
        } catch (ReflectiveOperationException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("Station menu positioning failed", e); }
        addTextField(new GuiTextFieldNop(30, this, guiLeft + 8, guiTop + 8, 160, 20, editing.name));
        if (container.kind == 1) {
            addLabel(new GuiLabel(1, "cnpcplus.station.inputs", guiLeft + 8, guiTop + 30));
            addLabel(new GuiLabel(2, "cnpcplus.station.outputs", guiLeft + 110, guiTop + 30));
        }
        int fieldId = container.kind == 0 ? 32 : 31;
        addTextField(new GuiTextFieldNop(fieldId, this, guiLeft + 8, guiTop + 92, 40, 18,
                Integer.toString(container.kind == 0 ? editing.experience : editing.time)));
        // CNPC numbersOnly rejects '-' typed from the keyboard; grindstone XP must allow negatives.
        getTextField(fieldId).floatsOnly = true;
        getTextField(fieldId).setMinMaxDefault(container.kind == 0 ? -32767f : 1f, container.kind == 0 ? 32767f : 100000f, container.kind == 0 ? 0f : 200f);
        if (list == null) list = new GuiCustomScrollNop(this, 0);
        list.setSize(130, 204); list.guiLeft = guiLeft + 178; list.guiTop = guiTop + 8;
        list.setUnsortedList(StationRecipes.CLIENT.getOrDefault(container.kind, List.of()).stream().map(r -> r.id + " " + r.name).toList());
        list.setSelected(editing.id + " " + editing.name);
        addScroll(list);
        String[] labels = {"gui.add", "gui.remove", "gui.save", "availability.options"};
        for (int i = 0; i < labels.length; i++) addButton(new GuiButtonNop(this, i + 10, guiLeft + 314, guiTop + 8 + 24 * i, 98, 20, labels[i]));
        addButton(new GuiIconToggleButton(this, 14, guiLeft + 54, guiTop + 91, 20, 20,
                new ItemStack(Items.NAME_TAG), editing.nameOnly, Component.translatable("cnpcplus.nameonly")));
    }
    private void capture() {
        if (getTextField(30) != null) editing.name = getTextField(30).getValue();
        if (getTextField(31) != null && getTextField(31).isFloat()) editing.time = (int)getTextField(31).getFloat();
        if (getTextField(32) != null && getTextField(32).isFloat()) editing.experience = (int)getTextField(32).getFloat();
    }
    @Override public void buttonEvent(GuiButtonNop button) {
        GuiTextFieldNop.unfocus();
        capture();
        if (button.id == 10) { editing = new StationRecipe(); editing.name = "new" + System.currentTimeMillis() % 100000; notice = ""; send(2, -1); m_7856_(); }
        else if (button.id == 11) send(4, editing.id);
        else if (button.id == 12) { notice = ""; StationPacket.CHANNEL.sendToServer(new StationPacket(3, container.kind, editing.save(false))); }
        else if (button.id == 13) { GuiTextFieldNop.unfocus(); setSubGui(new noppes.npcs.client.gui.SubGuiNpcAvailability(editing.availability)); }
        else if (button.id == 14) editing.nameOnly = button.getValue() == 1;
    }
    @Override public void subGuiClosed(net.minecraft.client.gui.screens.Screen screen) { capture(); m_7856_(); }
    private void send(int operation, int id) { CompoundTag tag = new CompoundTag(); tag.putInt("Id", id); StationPacket.CHANNEL.sendToServer(new StationPacket(operation, container.kind, tag)); }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {
        int index = scroll.getSelectedIndex();
        var recipes = StationRecipes.CLIENT.getOrDefault(container.kind, List.of());
        if (index < 0 || index >= recipes.size()) return;
        editing = StationRecipe.read(recipes.get(index).save(true)); send(2, editing.id); m_7856_();
    }
    @Override public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {}
    @Override protected void m_7286_(GuiGraphics graphics, float partial, int x, int y) {
        super.m_7286_(graphics, partial, x, y);
        // GuiBasicContainer temporarily clears slots while rendering a subgui (real crash: Index 0/length 0).
        if (hasSubGui()) return;
        if (container.kind == 1) {
            ResourceLocation brewing = new ResourceLocation("minecraft:textures/gui/container/brewing_stand.png");
            graphics.blit(brewing, guiLeft + 13, guiTop + 47, 54, 30, 64, 30);
            graphics.blit(brewing, guiLeft + 108, guiTop + 47, 54, 30, 64, 30);
        } else if (container.kind == 0) {
            // Entire vanilla support frame, both input recesses, arrow and result recess.
            graphics.blit(new ResourceLocation("minecraft:textures/gui/container/grindstone.png"),
                    guiLeft + 30, guiTop + 35, 30, 15, 116, 56);
        }
        if (container.kind != 0) {
            for (int i = 0; i < container.recipeSlots; i++) { var slot = container.getSlot(i); graphics.blit(new ResourceLocation("customnpcs:textures/gui/slot.png"), leftPos + slot.x - 1, topPos + slot.y - 1, 0, 0, 18, 18); }
            graphics.blit(new ResourceLocation("minecraft:textures/gui/container/furnace.png"), guiLeft + 81, guiTop + (container.kind == 1 ? 68 : 62), 176, 14, 24, 16);
        }
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        super.render(graphics, x, y, partial);
        if (hasSubGui()) return;
        if (!notice.isEmpty()) graphics.drawWordWrap(font, Component.translatable(notice), guiLeft + 314, guiTop + 108, 98, result > 0 ? 0x55FF55 : 0xFF5555);
        if (y >= guiTop + 92 && y < guiTop + 110 && x >= guiLeft + 8 && x < guiLeft + 48)
            graphics.renderTooltip(font, Component.translatable(container.kind == 0 ? "cnpcplus.station.experience" : "cnpcplus.station.time"), x, y);
    }
    public static void accept(int kind, CompoundTag tag) {
        List<StationRecipe> recipes = new ArrayList<>();
        var list = tag.getList("Recipes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) recipes.add(StationRecipe.read(list.getCompound(i)));
        StationRecipes.CLIENT.put(kind, recipes);
        if (Minecraft.getInstance().screen instanceof StationScreen screen && screen.container.kind == kind) {
            screen.capture();
            if (tag.contains("SelectedId")) screen.editing.id = tag.getInt("SelectedId");
            if (tag.contains("Notice")) {
                screen.notice = tag.getString("Notice");
                screen.result = tag.getInt("Result");
                if (screen.result == 1) {
                    for (StationRecipe recipe : recipes) if (recipe.id == screen.editing.id) screen.editing = StationRecipe.read(recipe.save(true));
                } else if (screen.result == 2) screen.editing = new StationRecipe();
            }
            screen.m_7856_();
        }
    }
}
