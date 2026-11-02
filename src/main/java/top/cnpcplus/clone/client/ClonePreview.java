package top.cnpcplus.clone.client;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import top.cnpcplus.client.gui.SafeEntityPreview;
import top.cnpcplus.clone.ClonePreviewData;
import top.cnpcplus.clone.ClonePreviewPacket;
import top.cnpcplus.text.client.PlayerTextPreview;

/** One detached preview per selection. Selection changes clear data before any IO or request. */
public final class ClonePreview {
    private static final AtomicLong REQUESTS = new AtomicLong();
    private final int slot;
    private int mode = -1, tab;
    private String name;
    private long request, changed;
    private boolean started, failed;
    private ClientLevel level;
    private LocalPlayer player;
    private Future<byte[]> local;
    private EntityNPCInterface npc;

    public ClonePreview() { this(0); }
    public ClonePreview(int slot) { this.slot = slot; }

    public void clear() {
        if (local != null) local.cancel(false);
        local = null; npc = null; name = null; mode = -1; request = 0;
        level = null; player = null; started = failed = false;
    }
    public void select(int source, int selectedTab, String selectedName) {
        var mc = Minecraft.getInstance();
        if (selectedName == null || !ClonePreviewData.validSelection(selectedTab, selectedName)) { clear(); return; }
        if (mode == source && tab == selectedTab && Objects.equals(name, selectedName) && level == mc.level && player == mc.player) return;
        clear();
        mode = source; tab = selectedTab; name = selectedName; level = mc.level; player = mc.player;
        request = REQUESTS.incrementAndGet(); changed = System.nanoTime();
    }
    public void render(GuiBasic owner, GuiGraphics g, int x, int y, int width, int height) {
        var mc = Minecraft.getInstance();
        if (owner.hasSubGui()) { clear(); return; }
        if (mc.level != level || mc.player != player) clear();
        g.fill(x, y, x + width, y + height, 0xFF000000);
        if (name == null) return;
        if (!started && System.nanoTime() - changed >= TimeUnit.MILLISECONDS.toNanos(200)) {
            started = true;
            try {
                if (mode == 0) local = ClonePreviewPacket.readAsync(new File(CustomNpcs.Dir, "clones"),
                        mc.getSingleplayerServer() == null ? null : CustomNpcs.getLevelSaveDirectory(), tab, name);
                else if (mode == 2) ClonePreviewPacket.send(slot, tab, name, request);
                else failed = true; // Generic mod entity constructors are not part of an NPC-only preview.
            } catch (RuntimeException | LinkageError e) { failed = true; }
        }
        if (local != null && local.isDone()) {
            try { receive(tab, name, request, local.get()); }
            catch (Exception e) { failed = true; }
            finally { local = null; }
        }
        if (started && npc == null && System.nanoTime() - changed > TimeUnit.SECONDS.toNanos(6)) {
            request = 0;
            if (local != null) local.cancel(false);
            local = null;
            failed = true;
        }
        if (npc == null) {
            g.drawWordWrap(mc.font, Component.translatable(failed ? "cnpcplus.clone.previewUnavailable" : "cnpcplus.clone.previewLoading"),
                    x + 4, y + 4, width - 8, 0xFFE0E0E0);
            return;
        }
        g.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        try {
            float zoom = Math.min(height / 72f, width / 55f);
            if (!SafeEntityPreview.nativeDisplay(g, npc, width / 2, height - 8, zoom, 180, 0, 0, x, y, false, false)) {
                npc = null; failed = true;
            }
        } finally { g.disableScissor(); }
    }
    public boolean receive(int selectedTab, String selectedName, long nonce, byte[] bytes) {
        var mc = Minecraft.getInstance();
        if (request == 0 || request != nonce || tab != selectedTab || !Objects.equals(name, selectedName)
                || level != mc.level || player != mc.player) return false;
        request = 0; // Consume success and failure alike; duplicate deliveries cannot overwrite the preview.
        try {
            var data = ClonePreviewData.decode(bytes);
            npc = data == null ? null : PlayerTextPreview.npc(data);
            failed = npc == null;
        } catch (Exception | LinkageError e) { npc = null; failed = true; }
        return true;
    }
    public static void accept(int tab, String name, long request, byte[] bytes) {
        Screen screen = Minecraft.getInstance().screen;
        while (screen instanceof GuiBasic basic && basic.hasSubGui()) screen = basic.getSubGui();
        if (screen instanceof ClonePreviewHost host) {
            host.cnpcplus$receivePreview(tab, name, request, bytes);
        }
    }
}
