package bin.cnpcplus.villagertrade.client;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.villagertrade.PacketVillageAction;
import bin.cnpcplus.villagertrade.PacketVillageState;
import bin.cnpcplus.villagertrade.TradeData;
import bin.cnpcplus.villagertrade.VillagerContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 原版 1.20.1 villager2.png + 同样的 276×166 三槽/左侧七行交易布局。 */
public final class VillagerScreen extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("cnpcplus", "textures/gui/villager2.png");
    private static PacketVillageState pending;
    private final VillagerContainer container;
    private final List<Integer> rows = new ArrayList<>();
    private GuiTextField count, refresh;
    private int scroll;
    private boolean dragging;

    public VillagerScreen(VillagerContainer container) { super(container); this.container = container; this.xSize = 276; this.ySize = 166; }

    public static void receive(PacketVillageState message) {
        if (message.data == null) return;
        pending = message;
        if (Minecraft.getMinecraft().currentScreen instanceof VillagerScreen) ((VillagerScreen) Minecraft.getMinecraft().currentScreen).acceptPending();
    }
    private void acceptPending() {
        if (pending == null || pending.window != container.windowId) return;
        container.trades.clear();
        NBTTagList list = pending.data.getTagList("Trades", 10);
        for (int i = 0; i < list.tagCount() && i < 64; i++) container.trades.add(TradeData.fromNBT(list.getCompoundTagAt(i)));
        boolean selectionChanged = container.selected != pending.selected;
        container.selected = pending.selected;
        pending = null;
        if (selectionChanged && count != null) { count.setFocused(false); refresh.setFocused(false); }
        rebuildRows(); updateFields();
    }

    @Override public void initGui() {
        super.initGui();
        for (int row = 0; row < 7; row++) this.addButton(new GuiButton(100 + row, guiLeft + 5, guiTop + 16 + row * 20, 88, 20, ""));
        if (container.editing) {
            int x = guiLeft - 100, y = guiTop + 10;
            this.addButton(new GuiButton(1, x, y, 95, 20, I18n.format("cnpcplus.villager.add")));
            this.addButton(new GuiButton(2, x, y + 25, 95, 20, I18n.format("gui.remove")));
            this.addButton(new GuiButton(3, x, y + 50, 95, 20, ""));
            this.addButton(new GuiButton(4, x, y + 95, 95, 20, ""));
            count = new GuiTextField(40, this.fontRenderer, x, y + 75, 40, 16);
            refresh = new GuiTextField(41, this.fontRenderer, x + 50, y + 75, 45, 16);
            count.setMaxStringLength(5); refresh.setMaxStringLength(8);
        }
        rebuildRows(); updateFields(); acceptPending();
    }

    private void rebuildRows() {
        rows.clear();
        for (int i = 0; i < container.trades.size(); i++) if (container.editing || !container.trades.get(i).output.isEmpty()) rows.add(i);
        scroll = Math.max(0, Math.min(Math.max(0, rows.size() - 7), scroll));
        for (GuiButton button : buttonList) if (button.id >= 100 && button.id < 107) button.visible = scroll + button.id - 100 < rows.size();
    }
    private void updateFields() {
        TradeData trade = container.current();
        if (count != null) {
            count.setEnabled(trade != null); refresh.setEnabled(trade != null);
            if (!count.isFocused()) count.setText(trade == null ? "" : Integer.toString(trade.limitCount));
            if (!refresh.isFocused()) refresh.setText(trade == null ? "" : Double.toString(trade.refreshSeconds));
        }
        for (GuiButton button : buttonList) {
            if (button.id == 2 || button.id == 3 || button.id == 4) button.enabled = trade != null;
            if (button.id == 3) button.displayString = I18n.format(trade != null && trade.limit ? "cnpcplus.villager.limit.on" : "cnpcplus.villager.limit.off");
            if (button.id == 4) button.displayString = I18n.format(trade != null && trade.nameOnly ? "cnpcplus.villager.name.on" : "cnpcplus.villager.name.off");
        }
    }

    @Override protected void actionPerformed(GuiButton button) {
        if (button.id >= 100 && button.id < 107) {
            int row = scroll + button.id - 100;
            if (row < rows.size()) send(PacketVillageAction.SELECT, rows.get(row), new NBTTagCompound());
            return;
        }
        if (!container.editing) return;
        if (button.id == 1) send(PacketVillageAction.ADD, -1, new NBTTagCompound());
        if (button.id == 2) send(PacketVillageAction.REMOVE, container.selected, new NBTTagCompound());
        TradeData trade = container.current();
        if (trade == null) return;
        if (button.id == 3) { trade.limit = !trade.limit; saveSettings(); updateFields(); }
        if (button.id == 4) { trade.nameOnly = !trade.nameOnly; saveSettings(); updateFields(); }
    }

    private void saveSettings() {
        TradeData trade = container.current();
        if (!container.editing || trade == null) return;
        try { trade.limitCount = Math.max(1, Math.min(10000, Integer.parseInt(count.getText()))); }
        catch (NumberFormatException ignored) { }
        try { double seconds = Double.parseDouble(refresh.getText()); if (Double.isFinite(seconds)) trade.refreshSeconds = Math.max(0, Math.min(99999, seconds)); }
        catch (NumberFormatException ignored) { }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("LimitOn", trade.limit); tag.setBoolean("NameOnly", trade.nameOnly);
        tag.setInteger("LimitCount", trade.limitCount); tag.setDouble("Refresh", trade.refreshSeconds);
        send(PacketVillageAction.SETTINGS, container.selected, tag);
    }
    private void send(int action, int selected, NBTTagCompound tag) {
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketVillageAction(action, container.windowId, selected, tag));
    }

    @Override protected void keyTyped(char character, int key) throws IOException {
        if (count != null && (count.textboxKeyTyped(character, key) || refresh.textboxKeyTyped(character, key))) { saveSettings(); return; }
        super.keyTyped(character, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        if (count != null) { count.mouseClicked(x, y, button); refresh.mouseClicked(x, y, button); }
        if (button == 0 && x >= guiLeft + 94 && x <= guiLeft + 100 && y >= guiTop + 18 && y <= guiTop + 158) { dragging = true; drag(y); return; }
        super.mouseClicked(x, y, button);
    }
    private void drag(int y) { scroll = Math.max(0, Math.min(Math.max(0, rows.size() - 7), (y - guiTop - 18) * Math.max(0, rows.size() - 7) / 140)); rebuildRows(); }
    @Override protected void mouseClickMove(int x, int y, int button, long time) { if (dragging) drag(y); else super.mouseClickMove(x, y, button, time); }
    @Override protected void mouseReleased(int x, int y, int button) { dragging = false; super.mouseReleased(x, y, button); }
    @Override public void handleMouseInput() throws IOException {
        super.handleMouseInput(); int delta = Mouse.getEventDWheel();
        if (delta != 0) { scroll += delta > 0 ? -1 : 1; rebuildRows(); }
    }
    @Override public void updateScreen() {
        super.updateScreen(); acceptPending();
        if (count != null) { count.updateCursorCounter(); refresh.updateCursorCounter(); }
    }
    @Override public void onGuiClosed() { if (container.editing) saveSettings(); super.onGuiClosed(); }

    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        drawModalRectWithCustomSizedTexture(guiLeft, guiTop, 0, 0, 276, 166, 512, 256);
        int thumb = rows.size() <= 7 ? 140 : Math.max(12, 140 * 7 / rows.size());
        int y = guiTop + 18 + (140 - thumb) * scroll / Math.max(1, rows.size() - 7);
        drawRect(guiLeft + 94, y, guiLeft + 99, y + thumb, 0xFFC5C5C5);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = container.npc == null ? I18n.format("role.villagertrader") : container.npc.getDisplayName().getUnformattedText();
        this.fontRenderer.drawString(title, 108, 6, 4210752);
        this.fontRenderer.drawString(this.mc.player.inventory.getDisplayName().getUnformattedText(), 108, 72, 4210752);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); super.drawScreen(mouseX, mouseY, partialTicks);
        ItemStack hovered = ItemStack.EMPTY;
        RenderHelper.enableGUIStandardItemLighting();
        for (int row = 0; row < 7 && row + scroll < rows.size(); row++) {
            int index = rows.get(row + scroll);
            TradeData trade = container.trades.get(index);
            trade.refresh(System.currentTimeMillis());
            int y = guiTop + 18 + row * 20;
            ItemStack[] items = {trade.a, trade.b, trade.output}; int[] xs = {guiLeft + 7, guiLeft + 31, guiLeft + 71};
            for (int i = 0; i < items.length; i++) {
                this.itemRender.renderItemAndEffectIntoGUI(items[i], xs[i], y);
                this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, items[i], xs[i], y, null);
                if (!items[i].isEmpty() && mouseX >= xs[i] && mouseX < xs[i] + 16 && mouseY >= y && mouseY < y + 16) hovered = items[i];
            }
            boolean exhausted = trade.uses >= (trade.limit ? trade.limitCount : trade.maxUses);
            this.fontRenderer.drawString(exhausted ? "X" : "→", guiLeft + 57, y + 4, exhausted ? 0xFF4444 : 0xEEEEEE);
            if (container.editing && container.selected == index) drawRect(guiLeft + 5, y - 2, guiLeft + 7, y + 17, 0xFFFFFF80);
        }
        RenderHelper.disableStandardItemLighting();
        if (count != null) { count.drawTextBox(); refresh.drawTextBox(); }
        renderHoveredToolTip(mouseX, mouseY);
        if (!hovered.isEmpty()) renderToolTip(hovered, mouseX, mouseY);
        if (count != null && mouseY >= count.y && mouseY < count.y + 16) {
            if (mouseX >= count.x && mouseX < count.x + 40) this.drawHoveringText(I18n.format("cnpcplus.villager.limitcount"), mouseX, mouseY);
            if (mouseX >= refresh.x && mouseX < refresh.x + 45) this.drawHoveringText(I18n.format("cnpcplus.villager.refresh"), mouseX, mouseY);
        }
    }
}
