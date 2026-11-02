package bin.cnpcplus.wand.client;

import bin.cnpcplus.client.gui.DragSearchField;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.mixin.wand.IScrollPosition;
import bin.cnpcplus.transport.client.Pinyin;
import bin.cnpcplus.wand.PacketWandNpc;
import java.util.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import noppes.npcs.CustomItems;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.GuiNpcMobSpawnerAdd;
import noppes.npcs.client.gui.util.*;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.entity.EntityNPCInterface;
import org.lwjgl.input.Mouse;

/** Shared native-layout editor/copy menu; only detached client entities are rendered. */
public final class GuiWandNpcList extends GuiNPCInterface implements IScrollData, ICustomScrollListener, GuiYesNoCallback, PreviewReceiver {
    private final boolean copying;
    private final Map<String, Integer> data = new HashMap<>();
    private final Set<Integer> selected = new HashSet<>();
    private List<Integer> deleting = Collections.emptyList();
    private String search = "";
    private GuiCustomScroll list;
    private EntityNPCInterface preview;
    private int previewId = -1;
    private long previewRequest, copyRequest;
    private final long opened = System.nanoTime();
    public GuiWandNpcList() { copying = player.getHeldItemMainhand().getItem() == CustomItems.cloner; xSize = 420; ySize = 222; setBackground("menubg.png"); closeOnEsc = true; }
    @Override public void initPacket() { Client.sendData(EnumPacketServer.RemoteNpcsGet); }
    @Override public void initGui() {
        super.initGui();
        addLabel(new GuiNpcLabel(0, "remote.title", guiLeft + 8, guiTop + 8));
        addTextField(new DragSearchField(0, this, guiLeft + 124, guiTop + 6, 200, 20, search));
        if (list == null) list = new GuiCustomScroll(this, 0) {
            private boolean dragging, moved;
            private int startX, startY;
            private Set<Integer> before = new HashSet<>();
            @Override public void mouseClicked(int x, int y, int button) {
                if (button != 0 || x < guiLeft + 4 || x >= guiLeft + getXSize() - 12 || y < guiTop + 4 || y >= guiTop + getYSize() - 4) return;
                dragging = true; moved = false; startX = x; startY = y; before = isCtrlKeyDown() ? new HashSet<>(GuiWandNpcList.this.selected) : new HashSet<>();
                GuiWandNpcList.this.selected.clear(); GuiWandNpcList.this.selected.addAll(before);
                int row = (y - guiTop - 4 + ((IScrollPosition) (Object) this).cnpcplus$scrollY()) / 14;
                if (row >= 0 && row < getList().size()) GuiWandNpcList.this.selected.add(data.get(getList().get(row)));
                highlight(); selectionChanged(); GuiNpcTextField.unfocus(); getTextField(0).setFocused(false);
            }
            @Override protected void drawItems() {
                // Native multiselect compares truncated labels: draw with the stable full-row identity instead.
                int scroll = ((IScrollPosition) (Object) this).cnpcplus$scrollY();
                for (int i = 0; i < getList().size(); i++) {
                    int y = 4 + i * 14 - scroll; if (y < 4 || y + 12 >= getYSize()) continue;
                    String label = getList().get(i);
                    if (GuiWandNpcList.this.selected.contains(data.get(label))) drawRect(2, y - 2, getXSize() - 12, y + 11, 0x5533AA55);
                    fontRenderer.drawString(fontRenderer.trimStringToWidth(label, getXSize() - 20), 4, y, GuiWandNpcList.this.selected.contains(data.get(label)) ? 0x00FF00 : 0xFFFFFF);
                }
            }
            @Override public void drawScreen(int x, int y, float partial, int wheel) {
                super.drawScreen(x, y, partial, wheel);
                if (dragging && !Mouse.isButtonDown(0)) dragging = false;
                if (!dragging) return;
                moved |= Math.abs(x - startX) > 3 || Math.abs(y - startY) > 3;
                if (!moved) return;
                GuiWandNpcList.this.selected.clear(); GuiWandNpcList.this.selected.addAll(before);
                int scroll = ((IScrollPosition) (Object) this).cnpcplus$scrollY();
                int low = Math.max(guiTop + 4, Math.min(startY, y)), high = Math.min(guiTop + getYSize() - 4, Math.max(startY, y));
                for (int row = 0; row < getList().size(); row++) {
                    int top = guiTop + 4 + row * 14 - scroll;
                    if (high >= top && low < top + 14) GuiWandNpcList.this.selected.add(data.get(getList().get(row)));
                }
                highlight(); selectionChanged();
                drawRect(Math.min(startX, x), low, Math.max(startX, x) + 1, high + 1, 0x3344AAFF);
            }
        };
        list.setSize(200, 170); list.guiLeft = guiLeft + 124; list.guiTop = guiTop + 30; addScroll(list);
        addButton(new GuiNpcButton(0, guiLeft + 330, guiTop + 6, 82, 20, copying ? "cnpcplus.wand.copy" : "selectServer.edit"));
        addButton(new GuiNpcButton(1, guiLeft + 330, guiTop + 30, 82, 20, "gui.remove"));
        addButton(new GuiNpcButton(4, guiLeft + 330, guiTop + 54, 82, 20, "remote.tp"));
        addButton(new GuiNpcButton(6, guiLeft + 330, guiTop + 78, 82, 20, "cnpcplus.wand.refresh"));
        if (!copying) {
            addButton(new GuiNpcButton(2, guiLeft + 330, guiTop + 110, 82, 20, "remote.reset"));
            addButton(new GuiNpcButton(5, guiLeft + 330, guiTop + 134, 82, 20, "remote.resetall"));
            addButton(new GuiNpcButton(3, guiLeft + 330, guiTop + 158, 82, 20, "remote.freeze"));
        }
        addLabel(new GuiNpcLabel(1, "cnpcplus.wand.selectionHelp", guiLeft + 8, guiTop + 205)); filter();
    }
    private void highlight() {
        HashSet<String> labels = new HashSet<>(); for (String label : list.getList()) if (selected.contains(data.get(label))) labels.add(label);
        list.setSelectedList(labels);
    }
    private void filter() {
        List<String> labels = new ArrayList<>(); for (String label : data.keySet()) if (Pinyin.matches(search, label)) labels.add(label);
        list.setList(labels); selected.retainAll(data.values()); selected.removeIf(id -> labels.stream().noneMatch(label -> id.equals(data.get(label))));
        highlight(); selectionChanged();
    }
    private void selectionChanged() {
        for (int id : new int[]{0, 1, 2, 4}) if (getButton(id) != null)
            getButton(id).setEnabled(id == 0 && copyRequest != 0 ? false : id == 1 || id == 2 ? !selected.isEmpty() : selected.size() == 1);
        int id = selected.size() == 1 ? selected.iterator().next() : -1;
        if (id == previewId) return;
        previewId = id; preview = null; previewRequest = System.nanoTime();
        if (id >= 0) CraftingViewNetwork.CHANNEL.sendToServer(new PacketWandNpc(id, 0, previewRequest, 0, ""));
    }
    @Override public void cnpcplus$receive(PacketWandNpc packet) {
        if (packet.action == 1) {
            if (!copying || copyRequest == 0 || copyRequest != packet.request) return;
            copyRequest = 0; selectionChanged(); if (packet.snapshot != null) displayGuiScreen(new GuiNpcMobSpawnerAdd(packet.snapshot)); return;
        }
        if (packet.action != 0 || packet.id != previewId || packet.request != previewRequest) return;
        Entity entity = packet.snapshot == null ? null : EntityList.createEntityFromNBT(packet.snapshot.copy(), mc.world);
        preview = entity instanceof EntityNPCInterface ? (EntityNPCInterface) entity : null;
    }
    @Override public void setData(Vector<String> names, HashMap<String, Integer> values) { data.clear(); data.putAll(values); filter(); }
    @Override public void setSelected(String label) { if (getButton(3) != null) getButton(3).setDisplayText(label); }
    @Override public void buttonEvent(GuiButton button) {
        List<Integer> ids = new ArrayList<>(selected);
        switch (button.id) {
            case 0:
                if (ids.size() != 1) return;
                if (copying) { copyRequest = System.nanoTime(); selectionChanged(); CraftingViewNetwork.CHANNEL.sendToServer(new PacketWandNpc(ids.get(0), 1, copyRequest, 0, "")); }
                else Client.sendData(EnumPacketServer.RemoteMainMenu, ids.get(0)); break;
            case 1: deleting = ids; displayGuiScreen(new GuiYesNo(this, "", net.minecraft.client.resources.I18n.format("gui.deleteMessage"), 0)); break;
            case 2: if (!copying) for (int id : ids) Client.sendData(EnumPacketServer.RemoteReset, id); break;
            case 3: if (!copying) Client.sendData(EnumPacketServer.RemoteFreeze); break;
            case 4: if (ids.size() == 1) { Client.sendData(EnumPacketServer.RemoteTpToNpc, ids.get(0)); close(); } break;
            case 5: if (!copying) for (int id : new HashSet<>(data.values())) Client.sendData(EnumPacketServer.RemoteReset, id); break;
            case 6: copyRequest = 0; previewId = -1; selectionChanged(); Client.sendData(EnumPacketServer.RemoteNpcsGet); break;
            default: break;
        }
    }
    @Override public void confirmClicked(boolean yes, int id) {
        if (yes) {
            for (int entity : deleting) Client.sendData(EnumPacketServer.RemoteDelete, entity);
            data.values().removeIf(deleting::contains); selected.removeAll(deleting); previewId = -1;
        }
        displayGuiScreen(this);
    }
    @Override public void keyTyped(char c, int key) throws java.io.IOException {
        super.keyTyped(c, key);
        if (!search.equals(getTextField(0).getText())) { search = getTextField(0).getText(); filter(); }
    }
    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll scroll) { selectionChanged(); }
    @Override public void scrollDoubleClicked(String label, GuiCustomScroll scroll) { }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        if (preview != null && !hasSubGui()) {
            int oldX = mouseX, oldY = mouseY; mouseX = guiLeft + 62; mouseY = guiTop + 122;
            drawNpc(preview, 62, 182, 1.7F, (int)((System.nanoTime() - opened) / 1_000_000_000.0 * 12) % 360);
            mouseX = oldX; mouseY = oldY;
        }
    }
    @Override public void save() { }
}
