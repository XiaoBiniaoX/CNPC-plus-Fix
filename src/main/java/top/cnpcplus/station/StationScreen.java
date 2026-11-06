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
import net.neoforged.neoforge.network.PacketDistributor;
import bin.cnpcplus.smelting.client.GuiIconToggleButton;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.shared.client.gui.components.*;
import noppes.npcs.shared.client.gui.listeners.*;

/** Same CNPC container, scroll, text fields and availability subpanel as the accepted editor. */
public final class StationScreen extends GuiContainerNPCInterface2<StationMenu> implements ICustomScrollListener {
    private final StationMenu container;
    private StationRecipe editing = new StationRecipe();
    private GuiCustomScrollNop list;
    private String notice = "";
    private int result;
    private int displayedId = Integer.MIN_VALUE;
    public StationScreen(StationMenu menu, Inventory inventory, Component title) {
        super(NoppesUtil.getLastNpc(), menu, inventory, title);
        container = menu;
        imageWidth = 420; imageHeight = 250; menuYOffset = 0;
        drawDefaultBackground = false;
        setBackground("inventorymenu.png");
    }
    @Override public void init() {
        if (displayedId == editing.id) capture();
        displayedId = editing.id;
        super.init();
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
        if (button.id == 10) { displayedId = Integer.MIN_VALUE; editing = new StationRecipe(); editing.name = "new" + System.currentTimeMillis() % 100000; notice = ""; send(2, -1); init(); }
        else if (button.id == 11) send(4, editing.id);
        else if (button.id == 12) { notice = ""; PacketDistributor.sendToServer(new StationPacket(3, container.kind, editing.saveMetadata(minecraft.level.registryAccess()))); }
        else if (button.id == 13) setSubGui(new noppes.npcs.client.gui.SubGuiNpcAvailability(editing.availability));
        else if (button.id == 14) editing.nameOnly = button.getValue() == 1;
    }
    @Override public void subGuiClosed(net.minecraft.client.gui.screens.Screen screen) { capture(); init(); }
    private void send(int operation, int id) { CompoundTag tag = new CompoundTag(); tag.putInt("Id", id); PacketDistributor.sendToServer(new StationPacket(operation, container.kind, tag)); }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) {
        int index = scroll.getSelectedIndex();
        var recipes = StationRecipes.CLIENT.getOrDefault(container.kind, List.of());
        if (index < 0 || index >= recipes.size()) return;
        displayedId = Integer.MIN_VALUE;
        editing = recipes.get(index).copy(); send(2, editing.id); init();
    }
    @Override public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {}
    @Override protected void renderBg(GuiGraphics graphics, float partial, int x, int y) {
        super.renderBg(graphics, partial, x, y);
        if (hasSubGui()) return;
        if (container.kind == 1) {
            ResourceLocation brewing = ResourceLocation.parse("minecraft:textures/gui/container/brewing_stand.png");
            graphics.blit(brewing, guiLeft + 13, guiTop + 47, 54, 30, 64, 30);
            graphics.blit(brewing, guiLeft + 108, guiTop + 47, 54, 30, 64, 30);
        } else if (container.kind == 0) {
            graphics.blit(ResourceLocation.parse("minecraft:textures/gui/container/grindstone.png"),
                    guiLeft + 30, guiTop + 35, 30, 15, 116, 56);
        }
        if (container.kind != 0) {
            for (int i = 0; i < container.recipeSlots; i++) { var slot = container.getSlot(i); graphics.blit(ResourceLocation.parse("customnpcs:textures/gui/slot.png"), leftPos + slot.x - 1, topPos + slot.y - 1, 0, 0, 18, 18); }
            graphics.blit(ResourceLocation.parse("minecraft:textures/gui/container/furnace.png"), guiLeft + 81, guiTop + (container.kind == 1 ? 68 : 62), 176, 14, 24, 16);
        }
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        super.render(graphics, x, y, partial);
        if (hasSubGui()) return;
        if (!notice.isEmpty()) graphics.drawWordWrap(font, Component.translatable(notice), guiLeft + 314, guiTop + 108, 98, result > 0 ? 0x55FF55 : 0xFF5555);
        if (y >= guiTop + 92 && y < guiTop + 110 && x >= guiLeft + 8 && x < guiLeft + 48)
            graphics.renderTooltip(font, Component.translatable(container.kind == 0 ? "cnpcplus.station.experience" : "cnpcplus.station.time"), x, y);
    }
    public static void accept(int kind, List<StationRecipe> recipes, CompoundTag tag) {
        StationRecipes.CLIENT.put(kind, recipes);
        if (Minecraft.getInstance().screen instanceof StationScreen screen && screen.container.kind == kind) {
            screen.capture();
            if (tag.contains("SelectedId")) screen.editing.id = tag.getInt("SelectedId");
            if (tag.contains("Notice")) {
                screen.notice = tag.getString("Notice");
                screen.result = tag.getInt("Result");
                if (screen.result == 1) {
                    screen.displayedId = Integer.MIN_VALUE;
                    for (StationRecipe recipe : recipes) if (recipe.id == screen.editing.id) screen.editing = recipe.copy();
                } else if (screen.result == 2) { screen.displayedId = Integer.MIN_VALUE; screen.editing = new StationRecipe(); }
            }
            screen.init();
        }
    }
}
