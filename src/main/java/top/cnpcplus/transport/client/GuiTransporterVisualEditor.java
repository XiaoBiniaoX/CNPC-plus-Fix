package top.cnpcplus.transport.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import top.cnpcplus.transport.TransportKeys;

/**
 * 任务241 传送师可视化编辑子页。
 * 241h：与玩家面板一模一样渲染 + 显示本组所有传送点 + 滚动条仅>4条目显示 + 拼音搜索。
 */
public class GuiTransporterVisualEditor extends GuiBasic implements ITransportPanelUI {
    private final TransportLocation location;
    private final int npcId;
    private float scale = 1f;
    private int originX;
    private int originY;

    private final List<String> filtered = new ArrayList<>();
    private String selectedName = "";
    private String search = "";
    private int offset;
    private boolean dragging;
    private int dragStartMouseY;
    private int dragStartOffset;
    private String lastClick = "";
    private long lastClickMs;

    public GuiTransporterVisualEditor(TransportLocation location, int npcId) {
        this.location = location;
        this.npcId = npcId;
        this.imageWidth = TransportLayout.DESIGN_W;
        this.imageHeight = TransportLayout.DESIGN_H;
        this.title = "";
        this.drawDefaultBackground = true;
        this.selectedName = location != null ? location.name : "";
    }

    private void layout() {
        float sx = (float) this.width / TransportLayout.DESIGN_W;
        float sy = (float) this.height / TransportLayout.DESIGN_H;
        float fit = Math.min(sx, sy);
        this.scale = Math.max(0.4f, Math.min(fit * 0.85f, 1.0f));
        this.originX = (int) ((this.width - TransportLayout.DESIGN_W * this.scale) / 2f);
        this.originY = (int) ((this.height - TransportLayout.DESIGN_H * this.scale) / 2f);
    }

    private int sx(int designX) { return this.originX + Math.round(designX * this.scale); }
    private int sy(int designY) { return this.originY + Math.round(designY * this.scale); }
    private int sw(int designW) { return Math.max(1, Math.round(designW * this.scale)); }
    private int sh(int designH) { return Math.max(1, Math.round(designH * this.scale)); }

    @Override
    public void m_7856_() {
        super.m_7856_();
        this.layout();
        TransportClientCache.requestMeta(this.npcId, true);
        GuiTextFieldNop searchField = new GuiTextFieldNop(
                TransportLayout.BTN_SEARCH_ID, this,
                this.sx(TransportLayout.SEARCH_FIELD_X),
                this.sy(TransportLayout.SEARCH_FIELD_Y),
                this.sw(TransportLayout.SEARCH_FIELD_W),
                this.sh(TransportLayout.SEARCH_FIELD_H),
                "");
        searchField.setBordered(false);
        searchField.setMaxLength(64);
        searchField.setResponder(s -> {
            this.search = s == null ? "" : s.trim();
            this.applyFilter();
        });
        this.addTextField(searchField);
        for (int i = 0; i < TransportLayout.ROW_VISIBLE; i++) {
            int designY = TransportLayout.ROW_Y + i * TransportLayout.ROW_STRIDE;
            this.addButton(new GuiTransportRowButton(
                    this,
                    TransportLayout.BTN_ROW_ID_BASE + i,
                    this.sx(TransportLayout.ROW_X),
                    this.sy(designY),
                    this.sw(TransportLayout.ROW_W),
                    this.sh(TransportLayout.ROW_H)));
        }
        this.applyFilter();
    }

    private void applyFilter() {
        this.filtered.clear();
        String q = Pinyin.normalizeQuery(this.search);
        java.util.List<String> all = new ArrayList<>(TransportClientCache.allMeta().keySet());
        all.sort(String::compareTo);
        for (String name : all) {
            if (q.isEmpty()
                    || Pinyin.fuzzy(q, name.toLowerCase(Locale.ROOT))
                    || Pinyin.fuzzy(q, Pinyin.normalizeQuery(Pinyin.of(name)))) {
                this.filtered.add(name);
            }
        }
        int maxOff = Math.max(0, this.filtered.size() - TransportLayout.ROW_VISIBLE);
        if (this.offset > maxOff) this.offset = maxOff;
        if (this.offset < 0) this.offset = 0;
        this.refreshRows();
    }

    private void refreshRows() {
        for (int i = 0; i < TransportLayout.ROW_VISIBLE; i++) {
            GuiButtonNop btn = this.getButton(TransportLayout.BTN_ROW_ID_BASE + i);
            if (!(btn instanceof GuiTransportRowButton row)) continue;
            int idx = this.offset + i;
            if (idx < this.filtered.size()) {
                row.visible = true;
                row.entryName = this.filtered.get(idx);
            } else {
                row.visible = false;
                row.entryName = "";
            }
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id < TransportLayout.BTN_ROW_ID_BASE
                || button.id >= TransportLayout.BTN_ROW_ID_BASE + TransportLayout.ROW_VISIBLE) {
            return;
        }
        if (!(button instanceof GuiTransportRowButton row)) return;
        String name = row.entryName;
        if (name == null || name.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (name.equals(this.selectedName)
                && now - this.lastClickMs <= TransportLayout.CLICK_MS
                && name.equals(this.lastClick)) {
            // 编辑页双击暂不传送（后续指示）
            return;
        }
        this.selectedName = name;
        this.lastClick = name;
        this.lastClickMs = now;
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.drawDefaultBackground) {
            this.renderBackground(graphics);
        }
        boolean bg = this.drawDefaultBackground;
        this.drawDefaultBackground = false;

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.originX, this.originY, 0);
        pose.scale(this.scale, this.scale, 1f);

        graphics.blit(TransportAssets.PANEL, 0, 0, 0f, 0f,
                TransportLayout.DESIGN_W, TransportLayout.DESIGN_H,
                TransportLayout.DESIGN_W, TransportLayout.DESIGN_H);
        graphics.blit(TransportAssets.TAB, TransportLayout.TAB_X, TransportLayout.TAB_Y,
                0f, 0f, TransportLayout.TAB_W, TransportLayout.TAB_H,
                TransportLayout.TAB_W, TransportLayout.TAB_H);
        graphics.blit(TransportAssets.TAB_STAR, TransportLayout.TAB_STAR_X, TransportLayout.TAB_STAR_Y,
                0f, 0f, TransportLayout.TAB_STAR, TransportLayout.TAB_STAR,
                TransportLayout.TAB_STAR, TransportLayout.TAB_STAR);
        graphics.blit(TransportAssets.SEARCH_BOX, TransportLayout.SEARCH_X, TransportLayout.SEARCH_Y,
                0f, 0f, TransportLayout.SEARCH_W, TransportLayout.SEARCH_H,
                TransportLayout.SEARCH_W, TransportLayout.SEARCH_H);
        graphics.blit(TransportAssets.SEARCH_ICON, TransportLayout.SEARCH_ICON_X, TransportLayout.SEARCH_ICON_Y,
                0f, 0f, TransportLayout.SEARCH_ICON, TransportLayout.SEARCH_ICON,
                TransportLayout.SEARCH_ICON, TransportLayout.SEARCH_ICON);
        graphics.blit(TransportAssets.LIST_GUTTER, TransportLayout.LIST_X, TransportLayout.LIST_Y,
                0f, 0f, TransportLayout.LIST_W, TransportLayout.LIST_H,
                TransportLayout.LIST_W, TransportLayout.LIST_H);

        this.drawScrollbar(graphics);
        this.drawCurrentPanel(graphics);
        this.drawImagePanel(graphics);
        this.drawIntro(graphics);

        pose.popPose();

        for (int i = 0; i < TransportLayout.ROW_VISIBLE; i++) {
            GuiButtonNop btn = this.getButton(TransportLayout.BTN_ROW_ID_BASE + i);
            if (btn != null && btn.visible && btn.shown) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        GuiTextFieldNop tf = this.getTextField(TransportLayout.BTN_SEARCH_ID);
        if (tf != null) {
            tf.render(graphics, mouseX, mouseY, partialTick);
        }

        super.m_88315_(graphics, mouseX, mouseY, partialTick);
        this.drawDefaultBackground = bg;
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int total = this.filtered.size();
        if (total <= TransportLayout.ROW_VISIBLE) return;
        int trackH = TransportLayout.SCROLL_TRACK_H;
        int thumbH = Math.max(30, trackH * TransportLayout.ROW_VISIBLE / total);
        int maxOff = Math.max(0, total - TransportLayout.ROW_VISIBLE);
        graphics.blit(TransportAssets.SCROLL_TRACK,
                TransportLayout.SCROLL_TRACK_X, TransportLayout.SCROLL_TRACK_Y,
                0f, 0f, TransportLayout.SCROLL_TRACK_W, trackH,
                TransportLayout.SCROLL_TRACK_W, trackH);
        int travel = trackH - thumbH;
        int thumbY = TransportLayout.SCROLL_TRACK_Y
                + (maxOff == 0 ? 0 : travel * this.offset / maxOff);
        int thumbX = TransportLayout.SCROLL_TRACK_X
                + (TransportLayout.SCROLL_TRACK_W - TransportLayout.SCROLL_THUMB_W) / 2;
        graphics.blit(TransportAssets.SCROLL_THUMB, thumbX, thumbY,
                0f, 0f, TransportLayout.SCROLL_THUMB_W, thumbH,
                TransportLayout.SCROLL_THUMB_W, 280);
    }

    private void drawCurrentPanel(GuiGraphics graphics) {
        graphics.blit(TransportAssets.CURRENT_PANEL,
                TransportLayout.CURRENT_X, TransportLayout.CURRENT_Y,
                0f, 0f, TransportLayout.CURRENT_W, TransportLayout.CURRENT_H,
                TransportLayout.CURRENT_W, TransportLayout.CURRENT_H);
        Font font = Minecraft.getInstance().font;
        CompoundTag meta = TransportClientCache.meta(this.selectedName);
        String icon = meta == null ? "" : meta.getString("Icon");
        this.drawIcon(graphics, TransportLayout.CURRENT_ICON_X, TransportLayout.CURRENT_ICON_Y,
                TransportLayout.CURRENT_ICON, icon);
        graphics.drawString(font, "当前位置",
                TransportLayout.CURRENT_LABEL_X, TransportLayout.CURRENT_LABEL_Y,
                0xFFCCCCCC, false);
        graphics.drawString(font, Component.literal(this.selectedName),
                TransportLayout.CURRENT_NAME_X, TransportLayout.CURRENT_NAME_Y,
                0xFFFFFFFF, false);
    }

    private void drawImagePanel(GuiGraphics graphics) {
        graphics.blit(TransportAssets.IMAGE_FRAME,
                TransportLayout.IMG_FRAME_X, TransportLayout.IMG_FRAME_Y,
                0f, 0f, TransportLayout.IMG_FRAME_W, TransportLayout.IMG_FRAME_H,
                TransportLayout.IMG_FRAME_W, TransportLayout.IMG_FRAME_H);
        CompoundTag meta = TransportClientCache.meta(this.selectedName);
        if (meta == null) return;
        String imgKey = meta.getString("Image");
        if (imgKey == null || imgKey.isEmpty()) return;
        TransportClientCache.requestImage(imgKey);
        var tex = TransportClientCache.imageId(imgKey);
        if (!TransportClientCache.hasImage(imgKey) || tex == null) return;
        int areaX = TransportLayout.IMG_FRAME_X + TransportLayout.IMG_PAD;
        int areaY = TransportLayout.IMG_FRAME_Y + TransportLayout.IMG_PAD;
        int areaW = TransportLayout.IMG_FRAME_W - TransportLayout.IMG_PAD * 2;
        int areaH = TransportLayout.IMG_FRAME_H - TransportLayout.IMG_PAD * 2;
        int mode = meta.getInt("ImageMode");
        if (mode < 0 || mode >= TransportKeys.MODE_COUNT) mode = TransportKeys.MODE_FILL;
        drawImageMode(graphics, tex, areaX, areaY, areaW, areaH, mode);
    }

    private void drawIntro(GuiGraphics graphics) {
        graphics.blit(TransportAssets.INTRO_PANEL,
                TransportLayout.INTRO_X, TransportLayout.INTRO_Y,
                0f, 0f, TransportLayout.INTRO_W, TransportLayout.INTRO_H,
                TransportLayout.INTRO_W, TransportLayout.INTRO_H);
        CompoundTag meta = TransportClientCache.meta(this.selectedName);
        if (meta == null) return;
        String desc = meta.getString("Desc");
        if (desc == null || desc.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int sc0x = this.sx(TransportLayout.INTRO_TEXT_X);
        int sc0y = this.sy(TransportLayout.INTRO_TEXT_Y);
        int sc1x = sc0x + this.sw(TransportLayout.INTRO_TEXT_W);
        int sc1y = this.sy(TransportLayout.INTRO_Y + TransportLayout.INTRO_H - 12);
        graphics.enableScissor(sc0x, sc0y, sc1x, sc1y);
        graphics.drawString(font, Component.literal(desc),
                TransportLayout.INTRO_TEXT_X, TransportLayout.INTRO_TEXT_Y, 0xFFE0E0E0, false);
        graphics.disableScissor();
    }

    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) return false;
        int total = this.filtered.size();
        int maxOff = Math.max(0, total - TransportLayout.ROW_VISIBLE);
        if (maxOff <= 0) return false;
        int lx = this.sx(TransportLayout.LIST_X);
        int ly = this.sy(TransportLayout.LIST_Y);
        int lw = this.sw(TransportLayout.LIST_W);
        int lh = this.sh(TransportLayout.LIST_H);
        if (mouseX < lx || mouseX > lx + lw || mouseY < ly || mouseY > ly + lh) return false;
        this.offset = Math.max(0, Math.min(maxOff, this.offset + (delta > 0 ? -1 : 1)));
        this.refreshRows();
        return true;
    }

    private void drawIcon(GuiGraphics graphics, int designX, int designY, int designSize, String iconName) {
        boolean empty = iconName == null || iconName.isEmpty();
        var rl = TransportAssets.icon(iconName);
        int tex = empty ? TransportAssets.DEFAULT_ICON_TEX : TransportAssets.ICON_TEX;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(designX, designY, 0);
        float s = (float) designSize / tex;
        pose.scale(s, s, 1f);
        graphics.blit(rl, 0, 0, 0f, 0f, tex, tex, tex, tex);
        pose.popPose();
    }

    private static void drawImageMode(GuiGraphics graphics, net.minecraft.resources.ResourceLocation tex,
                                      int x, int y, int w, int h, int mode) {
        if (mode == TransportKeys.MODE_STRETCH) {
            graphics.blit(tex, x, y, 0f, 0f, w, h, w, h);
            return;
        }
        if (mode == TransportKeys.MODE_TILE) {
            for (int ty = 0; ty < h; ty += 32) {
                for (int tx = 0; tx < w; tx += 32) {
                    graphics.blit(tex, x + tx, y + ty, 0f, 0f, 32, 32, 32, 32);
                }
            }
            return;
        }
        if (mode == TransportKeys.MODE_CENTER) {
            graphics.blit(tex, x + w / 2 - 64, y + h / 2 - 64, 0f, 0f, 128, 128, 128, 128);
            return;
        }
        graphics.blit(tex, x, y, 0f, 0f, w, h, w, h);
    }

    @Override
    public String cnpcplus$getSelected() { return this.selectedName; }

    @Override
    public void cnpcplus$setSelected(String name) { this.selectedName = name == null ? "" : name; }

    @Override
    public List<String> cnpcplus$getVisibleNames() { return this.filtered; }

    @Override
    public int cnpcplus$getOffset() { return this.offset; }

    @Override
    public void cnpcplus$setOffset(int offset) { this.offset = offset; }

    @Override
    public CompoundTag cnpcplus$getMeta(String name) {
        return TransportClientCache.meta(name);
    }
}
