package bin.cnpcplus.transport.client;

import bin.cnpcplus.transport.TransportTrade;
import java.io.IOException;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.client.gui.util.*;

/** Ghost slots copy inventory samples without moving or creating real items. */
public final class SubGuiTransportTrade extends SubGuiInterface implements ITextfieldListener {
    private final NBTTagCompound draft;
    private final ItemStack[] costs;
    private final boolean editor;
    private final Consumer<NBTTagCompound> commit;
    private ItemStack sample = ItemStack.EMPTY;
    private int selected = -1;

    public SubGuiTransportTrade(NBTTagCompound fare, boolean editor, Consumer<NBTTagCompound> commit) {
        draft = TransportTrade.clean(fare); costs = TransportTrade.costs(draft);
        this.editor = editor; this.commit = commit;
        xSize = 236; ySize = editor ? 244 : 148;
        closeOnEsc = true; setBackground("menubg.png"); title = "";
    }

    @Override public void initGui() {
        super.initGui();
        addLabel(new GuiNpcLabel(0, editor ? "cnpcplus.transport.trade" : "cnpcplus.transport.trade.confirm", guiLeft + 8, guiTop + 8));
        if (editor) {
            addButton(new GuiNpcButtonYesNo(0, guiLeft + 160, guiTop + 4, 66, 20, draft.getBoolean("TradeEnabled")));
            addLabel(new GuiNpcLabel(1, "cnpcplus.transport.trade.count", guiLeft + 92, guiTop + 40));
            addTextField(new GuiNpcTextField(1, this, fontRenderer, guiLeft + 154, guiTop + 36, 68, 20,
                    selected < 0 ? "1" : Integer.toString(costs[selected].getCount())));
            getTextField(1).numbersOnly = true;
            getTextField(1).setMinMaxDefault(1, selected < 0 || costs[selected].isEmpty() ? 64 : costs[selected].getMaxStackSize(), 1);
            getTextField(1).enabled = selected >= 0 && !costs[selected].isEmpty();
            addButton(new GuiNpcButton(3, guiLeft + 92, guiTop + 62, 130, 20, "cnpcplus.transport.trade.clear_sample"));
        }
        addButton(new GuiNpcButton(1, guiLeft + 12, guiTop + ySize - 28, 100, 20, editor ? "gui.save" : "gui.yes"));
        addButton(new GuiNpcButton(2, guiLeft + 124, guiTop + ySize - 28, 100, 20, "gui.cancel"));
    }

    @Override public void unFocused(GuiNpcTextField field) {
        if (selected >= 0 && !costs[selected].isEmpty()) costs[selected].setCount(field.getInteger());
    }
    @Override public void buttonEvent(GuiButton button) {
        if (editor && getTextField(1).enabled) unFocused(getTextField(1));
        if (button.id == 0) draft.setBoolean("TradeEnabled", ((GuiNpcButtonYesNo)button).getBoolean());
        if (button.id == 3) sample = ItemStack.EMPTY;
        if (button.id == 1) {
            NBTTagList list = new NBTTagList();
            for (ItemStack cost : costs) list.appendTag(cost.writeToNBT(new NBTTagCompound()));
            draft.setTag("TradeItems", list);
            close(); commit.accept(draft.copy());
        }
        if (button.id == 2) close();
    }

    @Override public void drawScreen(int x, int y, float partialTicks) {
        if (!editor) getButton(1).enabled = TransportTrade.payment(mc.player, draft) != null;
        super.drawScreen(x, y, partialTicks);
        ItemStack hovered = ItemStack.EMPTY;
        RenderHelper.enableGUIStandardItemLighting();
        for (int i = 0; i < 9; i++) {
            int px = guiLeft + 14 + i % 3 * 22, py = guiTop + 30 + i / 3 * 22;
            drawSlot(costs[i], px, py, selected == i);
            if (x >= px && x < px + 20 && y >= py && y < py + 20) hovered = costs[i];
        }
        if (editor) {
            for (int i = 0; i < mc.player.inventory.mainInventory.size(); i++) {
                int px = guiLeft + 16 + i % 9 * 22, py = guiTop + 118 + i / 9 * 22;
                ItemStack stack = mc.player.inventory.mainInventory.get(i);
                drawSlot(stack, px, py, false);
                if (x >= px && x < px + 20 && y >= py && y < py + 20) hovered = stack;
            }
        }
        RenderHelper.disableStandardItemLighting(); GlStateManager.disableDepth();
        fontRenderer.drawSplitString(I18n.format(editor ? "cnpcplus.transport.trade.edit_hint" : "cnpcplus.transport.trade.pay_hint"), guiLeft + 10, guiTop + 98, 218, 0x404040);
        if (!hovered.isEmpty()) renderToolTip(hovered, x, y);
        else if (editor && !sample.isEmpty()) drawHoveringText(java.util.Collections.singletonList(I18n.format("cnpcplus.transport.trade.sample", sample.getDisplayName())), x, y);
    }
    private void drawSlot(ItemStack stack, int x, int y, boolean selected) {
        GlStateManager.disableLighting(); GlStateManager.disableDepth();
        drawRect(x, y, x + 20, y + 20, selected ? 0xFFB3BD83 : 0xFF555555);
        GlStateManager.enableDepth(); GlStateManager.enableLighting();
        itemRender.renderItemAndEffectIntoGUI(stack, x + 2, y + 2);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x + 2, y + 2, null);
    }
    @Override public void mouseClicked(int x, int y, int button) throws IOException {
        if (editor) {
            if (getTextField(1).enabled) unFocused(getTextField(1));
            for (int i = 0; i < 9; i++) {
                int px = guiLeft + 14 + i % 3 * 22, py = guiTop + 30 + i / 3 * 22;
                if (x >= px && x < px + 20 && y >= py && y < py + 20) {
                    selected = i;
                    if (button == 1) costs[i] = ItemStack.EMPTY;
                    else if (!sample.isEmpty()) costs[i] = sample.copy();
                    initGui(); return;
                }
            }
            for (int i = 0; i < mc.player.inventory.mainInventory.size(); i++) {
                int px = guiLeft + 16 + i % 9 * 22, py = guiTop + 118 + i / 9 * 22;
                if (x >= px && x < px + 20 && y >= py && y < py + 20) { sample = mc.player.inventory.mainInventory.get(i).copy(); return; }
            }
        }
        super.mouseClicked(x, y, button);
    }
}
