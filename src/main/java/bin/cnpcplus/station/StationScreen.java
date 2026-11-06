package bin.cnpcplus.station;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.smelting.client.GuiIconToggleButton;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.util.*;
import java.lang.reflect.Field;
import java.util.*;

/** Same coordinates and controls as the accepted modern StationScreen. */
public final class StationScreen extends GuiContainerNPCInterface2 implements ICustomScrollListener, ITextfieldListener, ISubGuiListener {
    private final StationContainer container;
    private StationRecipe editing = new StationRecipe();
    private GuiCustomScroll list;
    private final List<Integer> ids = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private String notice = "";
    private int result;
    public StationScreen(StationContainer container) {
        super(noppes.npcs.client.NoppesUtil.getLastNpc(), container, 5); this.container = container;
        xSize = 420; ySize = 250; menuYOffset = 0; title = "";
        drawDefaultBackground = false; closeOnEsc = true; setBackground("inventorymenu.png");
    }
    @Override public void initGui() {
        super.initGui(); guiLeft = (width - xSize) / 2; guiTop = (height - ySize) / 2 + 28;
        String[][] fields = {{"field_147003_i", "guiLeft"}, {"field_147009_r", "guiTop"}};
        for (int i = 0; i < 2; i++) for (String name : fields[i]) {
            try { Field f = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField(name); f.setAccessible(true); f.setInt(this, i == 0 ? guiLeft : guiTop); break; }
            catch (NoSuchFieldException ignored) {}
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        try {
            Field field = GuiContainerNPCInterface2.class.getDeclaredField("menu"); field.setAccessible(true);
            Object menu = field.get(this); menu.getClass().getMethod("initGui", int.class, int.class, int.class).invoke(menu, guiLeft, guiTop + menuYOffset, xSize);
        } catch (ReflectiveOperationException error) { bin.cnpcplus.CnpcPlus.LOGGER.error("Station menu positioning failed", error); }
        addTextField(new GuiNpcTextField(30, this, getFontRenderer(), guiLeft + 8, guiTop + 8, 160, 20, editing.name));
        if (container.kind == 1) {
            addLabel(new GuiNpcLabel(1, "cnpcplus.station.inputs", guiLeft + 8, guiTop + 30));
            addLabel(new GuiNpcLabel(2, "cnpcplus.station.outputs", guiLeft + 110, guiTop + 30));
        }
        int id = container.kind == 0 ? 32 : 31;
        addTextField(new GuiNpcTextField(id, this, getFontRenderer(), guiLeft + 8, guiTop + 92, 40, 18,
                Integer.toString(container.kind == 0 ? editing.experience : editing.time)));
        getTextField(id).numbersOnly = true;
        getTextField(id).setMinMaxDefault(container.kind == 0 ? -32767 : 1, container.kind == 0 ? 32767 : 100000, container.kind == 0 ? 0 : 200);
        if (list == null) list = new GuiCustomScroll(this, 0);
        list.setSize(130, 204); list.guiLeft = guiLeft + 178; list.guiTop = guiTop + 8;
        list.setUnsortedList(names); list.selected = ids.indexOf(editing.id); addScroll(list);
        String[] labels = {"gui.add", "gui.remove", "gui.save", "availability.options"};
        for (int i = 0; i < 4; i++) addButton(new GuiNpcButton(10 + i, guiLeft + 314, guiTop + 8 + 24 * i, 98, 20, labels[i]));
        addButton(new GuiIconToggleButton(14, guiLeft + 54, guiTop + 91, 20, 20, new ItemStack(Items.NAME_TAG), editing.nameOnly, "cnpcplus.smelting.nameonly"));
    }
    private void capture() {
        if (getTextField(30) != null) editing.name = getTextField(30).getText();
        if (getTextField(31) != null && getTextField(31).isInteger()) editing.time = getTextField(31).getInteger();
        if (getTextField(32) != null && getTextField(32).isInteger()) editing.experience = getTextField(32).getInteger();
    }
    @Override public void unFocused(GuiNpcTextField field) { capture(); }
    @Override public void buttonEvent(GuiButton button) {
        GuiNpcTextField.unfocus(); capture();
        if (button.id == 10) { editing = new StationRecipe(); editing.name = "new" + System.currentTimeMillis() % 100000; send(2); }
        else if (button.id == 11) send(4);
        else if (button.id == 12) send(3);
        else if (button.id == 13) setSubGui(new noppes.npcs.client.gui.SubGuiNpcAvailability(editing.availability));
        else if (button.id == 14) editing.nameOnly = ((GuiIconToggleButton)button).isOn();
    }
    private void send(int action) {
        CraftingViewNetwork.CHANNEL.sendToServer(new StationPacket(action, container.kind, container.windowId, editing.save(false)));
    }
    @Override public void subGuiClosed(SubGuiInterface screen) { capture(); initGui(); }
    @Override public void save() {}
    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll source) {
        if (source.selected < 0 || source.selected >= ids.size()) return;
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Id", ids.get(source.selected));
        CraftingViewNetwork.CHANNEL.sendToServer(new StationPacket(1, container.kind, container.windowId, tag));
    }
    @Override public void scrollDoubleClicked(String name, GuiCustomScroll source) {}
    public void accept(StationPacket packet) {
        if (packet.window != container.windowId || packet.kind != container.kind) return;
        editing = StationRecipe.read(packet.data); ids.clear(); names.clear();
        NBTTagList recipes = packet.data.getTagList("Recipes", 10);
        for (int i = 0; i < recipes.tagCount(); i++) {
            NBTTagCompound tag = recipes.getCompoundTagAt(i); ids.add(tag.getInteger("Id")); names.add(tag.getInteger("Id") + " " + tag.getString("Name"));
        }
        notice = packet.data.getString("Notice"); result = packet.data.getInteger("Result"); initGui();
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        super.func_146976_a(partial, x, y); if (hasSubGui()) return;
        GlStateManager.color(1, 1, 1, 1);
        if (container.kind == 1) {
            mc.getTextureManager().bindTexture(new ResourceLocation("minecraft:textures/gui/container/brewing_stand.png"));
            drawTexturedModalRect(guiLeft + 13, guiTop + 47, 54, 30, 64, 30);
            drawTexturedModalRect(guiLeft + 108, guiTop + 47, 54, 30, 64, 30);
        }
        if (container.kind == 0) {
            mc.getTextureManager().bindTexture(new ResourceLocation("cnpcplus:textures/gui/grindstone.png"));
            drawTexturedModalRect(guiLeft + 30, guiTop + 35, 30, 15, 116, 56);
        } else {
            mc.getTextureManager().bindTexture(new ResourceLocation("customnpcs:textures/gui/slot.png"));
            for (int i = 0; i < container.recipeSlots; i++) { Slot slot = container.getSlot(i); drawTexturedModalRect(guiLeft + slot.xPos - 1, guiTop + slot.yPos - 1, 0, 0, 18, 18); }
            mc.getTextureManager().bindTexture(new ResourceLocation("minecraft:textures/gui/container/furnace.png"));
            drawTexturedModalRect(guiLeft + 81, guiTop + (container.kind == 1 ? 68 : 62), 176, 14, 24, 16);
        }
    }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial); if (hasSubGui()) return;
        if (!notice.isEmpty()) fontRenderer.drawSplitString(net.minecraft.client.resources.I18n.format(notice), guiLeft + 314, guiTop + 108, 98, result > 0 ? 0x55FF55 : 0xFF5555);
        if (y >= guiTop + 92 && y < guiTop + 110 && x >= guiLeft + 8 && x < guiLeft + 48)
            drawHoveringText(net.minecraft.client.resources.I18n.format(container.kind == 0 ? "cnpcplus.station.experience" : "cnpcplus.station.time"), x, y);
    }
}
