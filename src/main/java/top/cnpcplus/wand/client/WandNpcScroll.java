package top.cnpcplus.wand.client;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import top.cnpcplus.mixin.WandScrollAccess;
import top.cnpcplus.wand.WandTools;

/** Native CNPC list rendering/scrollbar; current-coordinate drag selection. */
final class WandNpcScroll extends GuiCustomScrollNop {
    private final GuiWandNpcList owner;
    private final Set<String> beforeDrag = new LinkedHashSet<>();
    private double startX, startY, mouseX, mouseY;
    private boolean dragging, rectangle;
    private long lastScroll;

    WandNpcScroll(GuiWandNpcList owner) {
        super(owner, 0, true);
        this.owner = owner;
        disabledSearch();
    }

    private WandScrollAccess access() { return (WandScrollAccess)(Object)this; }
    private int offset() { return access().cnpcplus$scrollY(); }

    @Override public boolean m_6375_(double x, double y, int button) { return false; } // Parent owns current-coordinate hit tests.

    @Override public boolean m_6050_(double x, double y, double amount) {
        if (amount == 0 || x < guiLeft || x >= guiLeft + getWidth() || y < guiTop || y >= guiTop + getHeight()) return false;
        access().cnpcplus$scrollY(Mth.clamp(offset() + (amount > 0 ? -14 : 14), 0, Math.max(0, access().cnpcplus$maxScrollY())));
        if (dragging && rectangle) selectRectangle();
        return true;
    }

    boolean pointerDown(double x, double y, int button) {
        if (button != 0 || x < guiLeft + 2 || x >= guiLeft + getWidth() - 12
                || y < guiTop + 2 || y >= guiTop + getHeight() - 2) return false;
        beforeDrag.clear();
        if (Screen.hasControlDown()) beforeDrag.addAll(getSelectedList());
        startX = mouseX = x;
        mouseY = y;
        startY = y + offset();
        dragging = true;
        rectangle = false;
        lastScroll = System.nanoTime();
        int row = (int)Math.floor((y - guiTop - 1 + offset()) / 14);
        Set<String> next = new LinkedHashSet<>(beforeDrag);
        if (row >= 0 && row < list.size()) {
            String label = list.get(row);
            if (!next.add(label)) next.remove(label);
            setSelected(label);
        } else setSelectedIndex(-1);
        setSelectedList(next);
        owner.selectionChanged();
        return true;
    }

    boolean pointerDrag(double x, double y, int button) {
        if (!dragging || button != 0) return false;
        mouseX = x;
        mouseY = y;
        if (Math.abs(x - startX) > 3 || Math.abs(y + offset() - startY) > 3) rectangle = true;
        if (rectangle) selectRectangle();
        return true;
    }

    boolean pointerUp(int button) {
        if (!dragging || button != 0) return false;
        cancelDrag();
        return true;
    }

    void cancelDrag() {
        dragging = false;
        rectangle = false;
        beforeDrag.clear();
    }

    private void selectRectangle() {
        Set<String> next = new LinkedHashSet<>(beforeDrag);
        double endY = Mth.clamp(mouseY, guiTop + 2, guiTop + getHeight() - 2) + offset();
        for (int i = 0; i < list.size(); i++) {
            double top = guiTop + 1 + i * 14;
            if (WandTools.intersects(startX, startY, mouseX, endY,
                    guiLeft + 2, top, guiLeft + getWidth() - 12, top + 14)) next.add(list.get(i));
        }
        setSelectedList(next);
        owner.selectionChanged();
    }

    @Override public void m_88315_(GuiGraphics graphics, int x, int y, float partial) {
        if (dragging && rectangle && System.nanoTime() - lastScroll >= 70_000_000L) {
            int direction = mouseY < guiTop + 12 ? -1 : mouseY > guiTop + getHeight() - 12 ? 1 : 0;
            access().cnpcplus$scrollY(Mth.clamp(offset() + direction * 14, 0, Math.max(0, access().cnpcplus$maxScrollY())));
            lastScroll = System.nanoTime();
            selectRectangle();
        }
        super.m_88315_(graphics, x, y, partial);
        if (dragging && rectangle) {
            int left = (int)Mth.clamp(Math.min(startX, mouseX), guiLeft + 2, guiLeft + getWidth() - 12);
            int right = (int)Mth.clamp(Math.max(startX, mouseX), guiLeft + 2, guiLeft + getWidth() - 12);
            int top = (int)Mth.clamp(Math.min(startY - offset(), mouseY), guiTop + 2, guiTop + getHeight() - 2);
            int bottom = (int)Mth.clamp(Math.max(startY - offset(), mouseY), guiTop + 2, guiTop + getHeight() - 2);
            graphics.fill(left, top, right, bottom, 0x3344BBDD);
            graphics.renderOutline(left, top, Math.max(1, right - left), Math.max(1, bottom - top), 0xFF44BBDD);
        }
    }
}
