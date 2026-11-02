package top.cnpcplus.wand.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import noppes.npcs.CustomItems;
import noppes.npcs.client.gui.GuiNpcRemoteEditor;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.*;
import noppes.npcs.shared.client.gui.components.*;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import top.cnpcplus.client.gui.SafeEntityPreview;
import top.cnpcplus.text.client.PlayerTextPreview;
import top.cnpcplus.wand.WandTools;
import top.cnpcplus.wand.network.WandNpcPacket;

/** One native page for NPC wand editing and cloner copying. */
public final class GuiWandNpcList extends GuiNpcRemoteEditor implements ICustomScrollListener {
    private final boolean copying;
    private final Map<String, Integer> data = new HashMap<>();
    private WandNpcScroll list;
    private String search = "";
    private EntityNPCInterface preview;
    private int previewId = -1;
    private long previewRequest, copyRequest;
    private final long opened = System.nanoTime();
    public GuiWandNpcList() {
        copying = player != null && player.getMainHandItem().getItem() == CustomItems.cloner;
        imageWidth = 420; imageHeight = 222;
    }
    @Override public void init() {
        super.init();
        GuiTextFieldNop.unfocus(); setFocused(null); setDragging(false);
        for (var child : new ArrayList<>(children())) removeWidget(child);
        wrapper.npcbuttons.clear(); wrapper.labels.clear(); wrapper.scrolls.clear();
        addLabel(new GuiLabel(0, "remote.title", guiLeft + 8, guiTop + 8));
        addLabel(new GuiLabel(1, "cnpcplus.wand.selectionHelp", guiLeft + 8, guiTop + 205));
        addTextField(new GuiTextFieldNop(0, this, guiLeft + 124, guiTop + 6, 200, 20, search));
        addWidget(getTextField(0));
        getTextField(0).setResponder(value -> { search = value; filter(); });
        if (list == null) list = new WandNpcScroll(this);
        list.setSize(200, 170); list.guiLeft = guiLeft + 124; list.guiTop = guiTop + 30; addScroll(list);
        addButton(new GuiButtonNop(this, 0, guiLeft + 330, guiTop + 6, 82, 20, copying ? "cnpcplus.wand.copy" : "selectServer.edit"));
        addButton(new GuiButtonNop(this, 1, guiLeft + 330, guiTop + 30, 82, 20, "selectServer.delete"));
        addButton(new GuiButtonNop(this, 4, guiLeft + 330, guiTop + 54, 82, 20, "remote.tp"));
        addButton(new GuiButtonNop(this, 6, guiLeft + 330, guiTop + 78, 82, 20, "cnpcplus.wand.refresh"));
        if (!copying) {
            addButton(new GuiButtonNop(this, 2, guiLeft + 330, guiTop + 110, 82, 20, "gui.reset"));
            addButton(new GuiButtonNop(this, 5, guiLeft + 330, guiTop + 134, 82, 20, "remote.resetall"));
            addButton(new GuiButtonNop(this, 3, guiLeft + 330, guiTop + 158, 82, 20, "remote.freeze"));
        }
        filter();
    }
    private void filter() { filter(selectedIds()); }
    private void filter(List<Integer> selected) {
        if (list == null) return;
        list.setList(new ArrayList<>(data.keySet().stream().filter(s -> WandTools.matches(search, s)).toList()));
        list.setSelectedList(WandTools.visibleSelection(data, selected, search)); list.cancelDrag(); selectionChanged();
    }
    private List<Integer> selectedIds() {
        return list == null ? List.of() : list.getSelectedList().stream().map(data::get).filter(java.util.Objects::nonNull).distinct().toList();
    }
    void selectionChanged() {
        var ids = selectedIds();
        for (int id : new int[]{0, 1, 2, 4}) if (getButton(id) != null)
            getButton(id).setEnabled(id == 0 && copyRequest != 0 ? false : id == 1 || id == 2 ? !ids.isEmpty() : ids.size() == 1);
        int id = ids.size() == 1 ? ids.get(0) : -1;
        if (id == previewId) return;
        previewId = id; preview = null; previewRequest = System.nanoTime();
        if (id >= 0) WandNpcPacket.send(id, previewRequest, false);
    }
    public void acceptPreview(int id, long request, CompoundTag snapshot) {
        if (id == previewId && request == previewRequest) preview = snapshot == null ? null : PlayerTextPreview.npc(snapshot);
    }
    public void acceptCopy(long request, CompoundTag snapshot) {
        if (!copying || copyRequest == 0 || request != copyRequest) return;
        copyRequest = 0; selectionChanged();
        if (snapshot != null) setScreen(new noppes.npcs.client.gui.GuiNpcMobSpawnerAdd(snapshot));
    }
    @Override public void setData(Vector<String> labels, Map<String, Integer> values) {
        var selected = selectedIds(); data.clear(); data.putAll(values); filter(selected);
    }
    @Override public void setSelected(String selected) { if (getButton(3) != null) getButton(3).setDisplayText(selected); }
    @Override public void buttonEvent(GuiButtonNop button) {
        var ids = selectedIds();
        switch (button.id) {
            case 0 -> {
                if (ids.size() != 1) return;
                if (copying) {
                    if (copyRequest != 0) return;
                    copyRequest = System.nanoTime(); selectionChanged(); WandNpcPacket.send(ids.get(0), copyRequest, true);
                } else Packets.sendServer(new SPacketRemoteMenuOpen(ids.get(0)));
            }
            case 1 -> {
                if (ids.isEmpty()) return;
                setScreen(new ConfirmScreen(yes -> {
                    if (yes) { ids.forEach(id -> Packets.sendServer(new SPacketRemoteNpcDelete(id))); data.entrySet().removeIf(entry -> ids.contains(entry.getValue())); filter(); }
                    setScreen(this); Packets.sendServer(new SPacketRemoteNpcsGet());
                }, Component.empty(), Component.translatable("gui.deleteMessage")));
            }
            case 2 -> { if (!copying) ids.forEach(id -> Packets.sendServer(new SPacketRemoteNpcReset(id))); }
            case 3 -> { if (!copying) Packets.sendServer(new SPacketRemoteFreeze()); }
            case 4 -> { if (ids.size() == 1) { Packets.sendServer(new SPacketRemoteNpcTp(ids.get(0))); close(); } }
            case 5 -> { if (!copying) data.values().stream().distinct().forEach(id -> Packets.sendServer(new SPacketRemoteNpcReset(id))); }
            case 6 -> { copyRequest = 0; previewId = -1; preview = null; if (list != null) list.cancelDrag(); selectionChanged(); Packets.sendServer(new SPacketRemoteNpcsGet()); }
            default -> { }
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (hasSubGui()) return wrapper.mouseClicked(x, y, button);
        if (list != null && list.pointerDown(x, y, button)) {
            GuiTextFieldNop.unfocus(); if (getTextField(0) != null) getTextField(0).setFocused(false); setFocused(null); return true;
        }
        if (wrapper.mouseClicked(x, y, button)) {
            if (getTextField(0) != null && getTextField(0).isFocused()) { setFocused(getTextField(0)); setDragging(button == 0); }
            return true;
        }
        for (var child : children()) if (child.mouseClicked(x, y, button)) { setFocused(child); return true; }
        setFocused(null); setDragging(false); return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (hasSubGui()) return wrapper.mouseDragged(x, y, button, dx, dy);
        return list != null && list.pointerDrag(x, y, button) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (hasSubGui()) return wrapper.mouseReleased(x, y, button);
        return list != null && list.pointerUp(button) || super.mouseReleased(x, y, button);
    }
    @Override public void scrollClicked(double x, double y, int button, GuiCustomScrollNop scroll) { selectionChanged(); }
    @Override public void scrollDoubleClicked(String selected, GuiCustomScrollNop scroll) {}
    @Override public void removed() {
        GuiTextFieldNop.unfocus(); setFocused(null); setDragging(false); preview = null; previewId = -1; previewRequest = copyRequest = 0;
        if (list != null) list.cancelDrag(); super.removed();
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        super.render(g, x, y, partial);
        if (preview != null && !hasSubGui()) {
            g.enableScissor(guiLeft + 4, guiTop + 26, guiLeft + 120, guiTop + 200);
            try {
                int rotation = (int)((System.nanoTime() - opened) / 1_000_000_000.0 * 12) % 360;
                if (!SafeEntityPreview.nativeDisplay(g, preview, 62, 182, 1.7F, rotation, x, y, guiLeft, guiTop, false, false)) preview = null;
            } finally { g.disableScissor(); }
        }
    }
}
