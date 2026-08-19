package bin.cnpcplus.smelting.client;

import bin.cnpcplus.smelting.ContainerSmeltingRecipes;
import bin.cnpcplus.smelting.SmeltingClientData;
import bin.cnpcplus.smelting.SmeltingLayout;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import noppes.npcs.client.gui.util.GuiTooltipUtils;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class SmeltingScreen extends GuiContainerNPCInterface2<ContainerSmeltingRecipes> implements ICustomScrollListener {
    private static final ResourceLocation FURNACE = ResourceLocation.withDefaultNamespace("textures/gui/container/furnace.png");
    private static final ResourceLocation SLOT = ResourceLocation.fromNamespaceAndPath("customnpcs", "textures/gui/slot.png");
    private static final int BTN_NEW = 3, BTN_REMOVE = 4, BTN_SAVE = 2, BTN_BLAST = 5, BTN_SMOKER = 6, BTN_GENERIC = 7;
    private final ContainerSmeltingRecipes container;
    private GuiCustomScrollNop scroll;
    private int selectedId = -1;
    private boolean blast, smoker, generic;
    private String lastRecipeSnapshot = "";

    public SmeltingScreen(ContainerSmeltingRecipes container, Inventory inv, Component title) {
        super(NoppesUtil.getLastNpc(), container, inv, title);
        this.container = container;
        this.drawDefaultBackground = false;
        this.setBackground("inventorymenu.png");
        this.imageWidth = 420;
        this.imageHeight = 250;
        this.title = "";
        this.selectedId = container.selectedId;
    }

    @Override public void init() {
        super.init();
        guiLeft += SmeltingLayout.guiOffsetX();
        guiTop += SmeltingLayout.guiOffsetY();
        leftPos = guiLeft;
        topPos = guiTop;
        repositionMenu();
        this.renderables.clear();
        this.setBackground("inventorymenu.png");
        if (scroll == null) scroll = new GuiCustomScrollNop(this, 0);
        scroll.setSize(130, 180); scroll.guiLeft = guiLeft + 172; scroll.guiTop = guiTop + 8; addScroll(scroll);
        addTextField(new GuiTextFieldNop(30, (Screen) this, guiLeft + SmeltingLayout.nameX(), guiTop + SmeltingLayout.nameY(), 160, 20, selectedName()));
        GuiTextFieldNop time = new GuiTextFieldNop(20, (Screen) this, guiLeft + SmeltingLayout.timeX(), guiTop + SmeltingLayout.timeY(), SmeltingLayout.fieldWidth(), SmeltingLayout.fieldHeight(), selectedCookTime());
        time.setFloatsOnly().setMinMaxDefault(0.01f, 100000.0f, 200.0f); addTextField(time);
        GuiTextFieldNop xp = new GuiTextFieldNop(21, (Screen) this, guiLeft + SmeltingLayout.xpX(), guiTop + SmeltingLayout.xpY(), SmeltingLayout.fieldWidth(), SmeltingLayout.fieldHeight(), selectedXp());
        xp.setFloatsOnly().setMinMaxDefault(-100000.0f, 100000.0f, 0.0f); addTextField(xp);
        addButton(new GuiButtonNop((IGuiInterface) this, BTN_NEW, guiLeft + SmeltingLayout.btnX(), guiTop + SmeltingLayout.btnY(), 84, 20, "gui.add"));
        addButton(new GuiButtonNop((IGuiInterface) this, BTN_REMOVE, guiLeft + SmeltingLayout.btnX(), guiTop + SmeltingLayout.btnY() + SmeltingLayout.btnSpacing(), 84, 20, "gui.remove"));
        addButton(new GuiButtonNop((IGuiInterface) this, BTN_SAVE, guiLeft + SmeltingLayout.btnX(), guiTop + SmeltingLayout.btnY() + SmeltingLayout.btnSpacing() * 2, 84, 20, "gui.save"));
        addButton(new GuiIconToggleButton((IGuiInterface) this, BTN_BLAST, guiLeft + SmeltingLayout.toggleX(), guiTop + SmeltingLayout.toggleY(), SmeltingLayout.toggleWidth(), SmeltingLayout.toggleHeight(), new ItemStack(Blocks.BLAST_FURNACE), blast, Component.translatable("cnpcplus.smelting.blast")));
        addButton(new GuiIconToggleButton((IGuiInterface) this, BTN_SMOKER, guiLeft + SmeltingLayout.toggleX(), guiTop + SmeltingLayout.toggleY() + SmeltingLayout.toggleSpacing(), SmeltingLayout.toggleWidth(), SmeltingLayout.toggleHeight(), new ItemStack(Blocks.SMOKER), smoker, Component.translatable("cnpcplus.smelting.smoker")));
        addButton(new GuiIconToggleButton((IGuiInterface) this, BTN_GENERIC, guiLeft + SmeltingLayout.toggleX(), guiTop + SmeltingLayout.toggleY() + SmeltingLayout.toggleSpacing() * 2, SmeltingLayout.toggleWidth(), SmeltingLayout.toggleHeight(), new ItemStack(Items.COAL), generic, Component.translatable("cnpcplus.smelting.generic")));
        refreshScroll();
    }

    private void repositionMenu() {
        try {
            Field field = GuiContainerNPCInterface2.class.getDeclaredField("menu");
            field.setAccessible(true);
            Object menu = field.get(this);
            if (menu == null) return;
            Method initGui = menu.getClass().getMethod("initGui", int.class, int.class, int.class);
            initGui.invoke(menu, guiLeft, guiTop + menuYOffset, imageWidth);
        } catch (ReflectiveOperationException ignored) {
            // CNPC 的顶部菜单不存在时，主体 UI 仍可正常显示。
        }
    }

    private List<SmeltingRecipeData> recipes() { return SmeltingClientData.get(); }
    private SmeltingRecipeData selected() { for (SmeltingRecipeData d : recipes()) if (d.id == selectedId) return d; return null; }
    private String selectedName() { SmeltingRecipeData d = selected(); return d == null ? "" : d.name; }
    private String selectedCookTime() { SmeltingRecipeData d = selected(); return d == null ? "200.0" : Float.toString(d.cookTime); }
    private String selectedXp() { SmeltingRecipeData d = selected(); return d == null ? "0.0" : Float.toString(d.xp); }
    private void refreshScroll() {
        List<String> names = new ArrayList<>();
        List<SmeltingRecipeData> list = recipes();
        for (int i = 0; i < list.size(); i++) {
            SmeltingRecipeData data = list.get(i);
            boolean duplicate = false;
            for (int j = 0; j < list.size(); j++) {
                if (i != j && list.get(j).name.equals(data.name)) { duplicate = true; break; }
            }
            names.add(duplicate ? data.name + " (" + data.id + ")" : data.name);
        }
        scroll.setList(names);
    }

    @Override public void buttonEvent(GuiButtonNop button) {
        if (button.id == BTN_NEW) {
            selectedId = -1;
            container.selectedId = -1;
            blast = smoker = generic = false;
            PacketDistributor.sendToServer(new PacketSmeltingAction(5, -1, "", 0, 0, false, false, false));
            init();
            if (getTextField(30) != null) getTextField(30).setValue("new_recipe");
            return;
        }
        if (button.id == BTN_REMOVE) { send(2); return; }
        if (button.id == BTN_SAVE) { send(1); return; }
        if (button.id == BTN_BLAST) blast = button.getValue() == 1;
        if (button.id == BTN_SMOKER) smoker = button.getValue() == 1;
        if (button.id == BTN_GENERIC) generic = button.getValue() == 1;
    }

    private void send(int action) {
        GuiTextFieldNop name = getTextField(30), time = getTextField(20), xp = getTextField(21);
        float cook = time == null || !time.isFloat() ? 200 : Math.max(0.01f, time.getFloat());
        float experience = xp == null || !xp.isFloat() ? 0 : xp.getFloat();
        PacketDistributor.sendToServer(new PacketSmeltingAction(action, selectedId, name == null ? "" : name.getValue(), cook, experience, blast, smoker, generic));
    }

    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop source) {
        int index = source.getSelectedIndex();
        List<SmeltingRecipeData> list = recipes();
        if (index < 0 || index >= list.size()) return;
        SmeltingRecipeData d = list.get(index); selectedId = d.id; container.selectedId = d.id;
        blast = d.blastAllowed; smoker = d.smokerAllowed; generic = d.genericFuelAllowed;
        PacketDistributor.sendToServer(new PacketSmeltingAction(4, d.id, "", 0, 0, false, false, false));
        init();
    }
    @Override public void scrollDoubleClicked(String selection, GuiCustomScrollNop source) {}

    @Override protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        String snapshot = recipeSnapshot();
        if (!snapshot.equals(lastRecipeSnapshot)) { lastRecipeSnapshot = snapshot; if (scroll != null) refreshScroll(); }
        super.renderBg(graphics, partialTicks, mouseX, mouseY);
        drawSlotBg(graphics, 0); drawSlotBg(graphics, 1); drawSlotBg(graphics, 2);
        int ticks = Minecraft.getInstance().gui.getGuiTicks();
        int flame = ticks / 2 % 14; graphics.blit(FURNACE, guiLeft + 57, guiTop + 67 - flame, 176, 12 - flame, 14, flame + 1);
        int cook = Math.max(20, Math.round(selected() == null ? 200 : selected().cookTime));
        int width = (ticks * 2) % cook * 24 / cook; graphics.blit(FURNACE, guiLeft + 79, guiTop + 55, 176, 14, width + 1, 16);
    }
    private void drawSlotBg(GuiGraphics g, int index) { if (index >= container.slots.size()) return; var slot = container.slots.get(index); g.blit(SLOT, leftPos + slot.x - 1, topPos + slot.y - 1, 0, 0, 18, 18); }

    private String recipeSnapshot() {
        StringBuilder snapshot = new StringBuilder();
        for (SmeltingRecipeData data : recipes()) snapshot.append(data.id).append(':').append(data.name).append(';');
        return snapshot.toString();
    }

    @Override public void containerTick() {
        String snapshot = recipeSnapshot();
        int serverSelectedId = SmeltingClientData.selectedId();
        if (serverSelectedId != selectedId) {
            selectedId = container.selectedId = serverSelectedId;
        }
        if (!snapshot.equals(lastRecipeSnapshot)) {
            lastRecipeSnapshot = snapshot;
            if (selectedId < 0) {
                List<SmeltingRecipeData> list = recipes();
                if (!list.isEmpty()) selectedId = container.selectedId = list.get(list.size() - 1).id;
            }
            init();
        }
        super.containerTick();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (hasSubGui()) return;
        GuiTextFieldNop time = getTextField(20);
        GuiTextFieldNop xp = getTextField(21);
        if (time != null && inside(time, mouseX, mouseY)) {
            GuiTooltipUtils.renderTooltip(graphics, this.font,
                    Component.translatable("cnpcplus.smelting.cooktime"), mouseX, mouseY);
        } else if (xp != null && inside(xp, mouseX, mouseY)) {
            GuiTooltipUtils.renderTooltip(graphics, this.font,
                    Component.translatable("cnpcplus.smelting.xp"), mouseX, mouseY);
        }
    }

    private static boolean inside(GuiTextFieldNop field, int mouseX, int mouseY) {
        return mouseX >= field.getX() && mouseX < field.getX() + field.getWidth()
                && mouseY >= field.getY() && mouseY < field.getY() + field.getHeight();
    }
}
