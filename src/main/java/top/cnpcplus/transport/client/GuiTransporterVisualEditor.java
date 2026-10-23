package top.cnpcplus.transport.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.TransportLocation;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGui;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.TransportPanelStyle;
import top.cnpcplus.transport.TransportVisualMeta;
import top.cnpcplus.transport.network.PacketTransportMetaSave;
import top.cnpcplus.transport.network.TransportPacketHandler;

/**
 * 任务241 传送师可视化编辑子页。
 * 241h：与玩家面板一模一样渲染 + 显示本组所有传送点 + 滚动条仅>4条目显示 + 拼音搜索。
 * 241j：面板样式（字号/颜色/坐标/图标尺寸）+ 拖 PNG 导入 + 退出即保存。
 * 241k/l：面板本体即编辑器——点面板里的文本/图标/图片出左边栏。
 * 241m（本次返修）：①边栏固定宽度并把面板缩到右侧（不再互相压，输入框不越界）
 * ②边栏内容可滚动（带滚动条）③补粗体/下划线/斜体按钮 ④图片 5 模式按源图比例 + 裁剪到框内。
 */
public class GuiTransporterVisualEditor extends GuiBasic
        implements ITransportPanelUI, TransportDropHandler.DropTarget {

    private static final int EL_ROW_TEXT = 0;
    private static final int EL_CUR_TEXT = 1;
    private static final int EL_INTRO = 2;
    private static final int EL_TAB_TEXT = 3;
    private static final int EL_ROW_ICON = 4;
    private static final int EL_CUR_ICON = 5;
    private static final int EL_TAB_ICON = 6;
    private static final int EL_IMAGE = 7;

    private static final String[] ELEM_LABEL = {
            "cnpcplus.transporter.elem.row_text", "cnpcplus.transporter.elem.cur_text",
            "cnpcplus.transporter.elem.intro", "cnpcplus.transporter.elem.tab_text",
            "cnpcplus.transporter.elem.row_icon", "cnpcplus.transporter.elem.cur_icon",
            "cnpcplus.transporter.elem.tab_icon", "cnpcplus.transporter.elem.image"};

    private static final int FIELD_SIZE_ID = 61;
    private static final int FIELD_ICON_PCT_ID = 62;
    private static final int FIELD_X_ID = 63;
    private static final int FIELD_Y_ID = 64;
    private static final int BTN_SAVE_ID = 60;
    private static final int AREA_CONTENT_ID = 67;

    private static final int CELL = 30;
    private static final int SWATCH = 14;
    private static final int SWATCH_COLS = 8;
    private static final long HINT_MS = 5000;
    private static final int ROW_H = 16;

    private static final int[] PALETTE = {
            0xFFFFFF, 0xE0E0E0, 0xCCCCCC, 0x999999, 0x666666, 0x333333, 0x000000, 0xFFD700,
            0xFF5555, 0xFF7F50, 0xFFAA00, 0xFFFF55, 0xAAFF55, 0x55FF55, 0x55FFFF, 0xFF55FF};



    /** 241m：格式按钮（MC 格式码，vanilla 字体原生支持 §l 粗体 / §n 下划线 / §o 斜体 / §r 重置）。 */
    private static final String[] FMT_LABEL = {
            "cnpcplus.transporter.editor.fmtBold", "cnpcplus.transporter.editor.fmtUnderline",
            "cnpcplus.transporter.editor.fmtItalic", "cnpcplus.transporter.editor.fmtReset"};
    /** 241q：格式按钮对应样式标志（1=粗 2=下划线 4=斜体；最后一个=清除）。 */
    private static final int[] FMT_FLAG = {
            top.cnpcplus.transport.TransportSpans.FLAG_BOLD,
            top.cnpcplus.transport.TransportSpans.FLAG_UNDERLINE,
            top.cnpcplus.transport.TransportSpans.FLAG_ITALIC,
            0};

    /** 图片显示模式按钮（顺序 = 界面顺序）。 */
    private static final int[] IMG_MODE_VALUE = {
            TransportKeys.MODE_CENTER, TransportKeys.MODE_STRETCH,
            TransportKeys.MODE_FIT, TransportKeys.MODE_TILE};
    private static final String[] IMG_MODE_LABEL = {
            "cnpcplus.transporter.editor.imageModeOriginal",
            "cnpcplus.transporter.editor.imageModeStretch",
            "cnpcplus.transporter.editor.imageModeFit",
            "cnpcplus.transporter.editor.imageModeTile"};

    private final TransportLocation location;
    private final int npcId;
    private float scale = 1f;
    private int originX;
    private int originY;

    private final List<String> filtered = new ArrayList<>();
    private final Map<String, String> renames = new HashMap<>();
    /** 241n：本地未落盘的改动（meta 响应到达后重新覆盖，避免被服务端旧值冲掉）。 */
    private final CompoundTag panelLocal = new CompoundTag();
    private final Map<String, CompoundTag> entryLocal = new HashMap<>();
    private String ownKey = "";
    private String selectedName = "";
    private String search = "";
    private int offset;
    private String lastClick = "";
    private long lastClickMs;

    private int activeElement = -1;
    private boolean dirty;
    private boolean edited;
    private boolean syncing;
    private String areaKey = "";
    /** 241z-8：GuiTextArea 不支持拖选，自己记录拖选锚点与拖选状态。 */
    private int areaAnchor = -1;
    private boolean areaDragging;
    /** 241q：传送点图标编辑时，当前改的是"普通图标"(0) 还是"选中图标"(1)。 */
    private int iconSelMode;
    private String hint = "";
    private long hintUntil;
    private int sbScroll;

    public GuiTransporterVisualEditor(TransportLocation location, int npcId) {
        this.location = location;
        this.npcId = npcId;
        this.imageWidth = TransportLayout.DESIGN_W;
        this.imageHeight = TransportLayout.DESIGN_H;
        this.title = "";
        this.drawDefaultBackground = true;
        this.closeOnEsc = true;
        this.selectedName = location != null ? location.name : "";
        this.ownKey = this.selectedName;
    }

    // ---- 布局：边栏固定宽在左，面板缩到右侧，两者不重叠 ----
    private int sbW() { return Math.max(190, Math.min(270, (int) (this.width * 0.28f))); }
    private int sbH() { return this.height; }
    private int sbHeadH() { return 24; }

    private void layout() {
        int sbw = this.sbW();
        int areaW = Math.max(120, this.width - sbw - 6);
        float sx = areaW / (float) TransportLayout.DESIGN_W;
        float sy = (this.height - 8) / (float) TransportLayout.DESIGN_H;
        float fit = Math.min(sx, sy);
        this.scale = Math.max(0.3f, Math.min(fit * 0.98f, 1.0f));
        this.originX = sbw + 3 + (int) ((areaW - TransportLayout.DESIGN_W * this.scale) / 2f);
        this.originY = (int) ((this.height - TransportLayout.DESIGN_H * this.scale) / 2f);
    }

    private int sx(int designX) { return this.originX + Math.round(designX * this.scale); }
    private int sy(int designY) { return this.originY + Math.round(designY * this.scale); }
    private int sw(int designW) { return Math.max(1, Math.round(designW * this.scale)); }
    private int sh(int designH) { return Math.max(1, Math.round(designH * this.scale)); }

    // ---- 边栏内部布局（内容区可滚动） ----
    private int sbContentY() { return this.sbHeadH() - this.sbScroll; }
    private int sbFieldW() { return Math.max(40, (this.sbW() - 16) / 2 - 30); }
    private int imageModeBtnW() {
        return Math.max(38, (this.sbW() - 8 - (IMG_MODE_VALUE.length - 1) * 4) / IMG_MODE_VALUE.length);
    }
    private int sbFullW() { return Math.max(60, this.sbW() - 16 - 30); }

    @Override
    public void m_7856_() {
        try {
            this.cnpcplus$init();
        } catch (Throwable t) {
            // 241n：初始化异常绝不能把游戏带崩（之前 mixin 失败就是这样整局崩的）
            top.cnpcplus.transport.TransportLog.error(
                    "[cnpcplus] transport editor init FAILED: " + t);
            t.printStackTrace();
        }
    }

    private void cnpcplus$init() {
        super.m_7856_();
        this.layout();
        TransportClientCache.requestMeta(this.npcId, true);
        // 241n：meta 到达后重建列表 + 重新覆盖本地改动（不再整体替换导致"改了没反应"）
        TransportClientCache.setMetaListener(() -> {
            this.applyLocal();
            this.applyFilter();
            if (TransportClientCache.consumeSavedAck()) {
                this.setHint(tr("cnpcplus.transporter.editor.hint.saved"));
            }
            if (TransportClientCache.consumeImageAck()) {
                this.setHint(tr("cnpcplus.transporter.editor.hint.imageSaved"));
            }
        });
        TransportDropHandler.setActive(this);

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
        this.addWidget(searchField);   // 241o：进入 vanilla children → 有光标/拖选/方向键

        this.addTextField(this.makeField(FIELD_SIZE_ID));
        this.addTextField(this.makeField(FIELD_ICON_PCT_ID));
        this.addTextField(this.makeField(FIELD_X_ID));
        this.addTextField(this.makeField(FIELD_Y_ID));



        GuiTextArea area = new GuiTextArea(AREA_CONTENT_ID, 4, this.sbHeadH(), this.sbW() - 16, 90, "");
        area.setListener(s -> {
            if (this.syncing) return;
            this.applyContent(s);
            // 241n：介绍文本是**每条目**的键 → 必须走条目通道，否则只发了面板、文本没落库
            this.commit(this.activeElement == EL_ROW_TEXT || this.activeElement == EL_INTRO);
        });
        this.add((IGui) area);

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
        this.addButton(new GuiButtonNop(this, BTN_SAVE_ID,
                this.width - 78, 4, 74, 18,
                tr("cnpcplus.transporter.editor.save")));
        this.applyFilter();
        this.syncFields();
    }

    private GuiTextFieldNop makeField(int id) {
        GuiTextFieldNop f = new GuiTextFieldNop(id, this, 0, 0, 40, 14, "");
        f.setMaxLength(64);
        f.enabled = false;
        f.visible = false;
        f.setResponder(s -> {
            if (this.syncing) return;
            this.onField(id, s);
        });
        // 241o：加入 vanilla children → GuiBasic.m_7522_(setFocused) 才不会拒绝它
        // （CNPC 的输入框默认不在 children 里 → vanilla 焦点被拒 → 没有光标/拖选/方向键）
        this.addWidget(f);
        return f;
    }

    private void onField(int id, String raw) {
        switch (id) {
            case FIELD_SIZE_ID -> {
                Integer v = parseInt(raw);
                if (v == null) return;
                this.applySize(v);
                this.commit(this.activeElement == EL_ROW_TEXT);
            }
            case FIELD_ICON_PCT_ID -> {
                Integer v = parseInt(raw);
                if (v == null) return;
                this.applyIconPct(v);
                this.commit(this.activeElement == EL_ROW_ICON);
            }
            case FIELD_X_ID -> {
                Integer v = parseInt(raw);
                if (v == null) return;
                this.applyOffset(true, v);
                this.commit(this.activeElement == EL_ROW_TEXT || this.activeElement == EL_ROW_ICON
                        || this.activeElement == EL_IMAGE);
            }
            case FIELD_Y_ID -> {
                Integer v = parseInt(raw);
                if (v == null) return;
                this.applyOffset(false, v);
                this.commit(this.activeElement == EL_ROW_TEXT || this.activeElement == EL_ROW_ICON
                        || this.activeElement == EL_IMAGE);
            }
            default -> { }
        }
    }

    private void applyFilter() {
        this.filtered.clear();
        String q = Pinyin.normalizeQuery(this.search);
        List<String> all = new ArrayList<>(TransportClientCache.allEditMeta().keySet());
        all.sort(String::compareTo);
        for (String name : all) {
            String shown = this.displayName(name);
            if (q.isEmpty()
                    || Pinyin.fuzzy(q, shown.toLowerCase(Locale.ROOT))
                    || Pinyin.fuzzy(q, Pinyin.normalizeQuery(Pinyin.of(shown)))) {
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

    private String displayName(String key) {
        String n = this.renames.get(key);
        String shown = n == null || n.isEmpty() ? key : n;
        // 241n：标出 NPC 本体点位（用户以为是"清理 NPC 后的遗留条目"）
        if (!this.ownKey.isEmpty() && key.equals(this.ownKey)) {
            return shown + tr("cnpcplus.transporter.editor.own");
        }
        return shown;
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == BTN_SAVE_ID) {
            this.saveNow();
            return;
        }
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
            return;
        }
        this.selectedName = name;
        this.lastClick = name;
        this.lastClickMs = now;
        this.syncFields();
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
        CompoundTag style = TransportClientCache.panel();
        String tabIcon = TransportPanelStyle.tabIcon(style);
        int tabIconPct = TransportPanelStyle.tabIconSize(style);
        int tabIconSize = Math.round(TransportLayout.TAB_STAR * tabIconPct / 100f);
        if (tabIcon.isEmpty()) {
            graphics.blit(TransportAssets.TAB_STAR,
                    TransportLayout.TAB_STAR_X + (TransportLayout.TAB_STAR - tabIconSize) / 2,
                    TransportLayout.TAB_STAR_Y + (TransportLayout.TAB_STAR - tabIconSize) / 2,
                    0f, 0f, tabIconSize, tabIconSize,
                    TransportLayout.TAB_STAR, TransportLayout.TAB_STAR);
        } else {
            this.drawIcon(graphics,
                    TransportLayout.TAB_STAR_X + (TransportLayout.TAB_STAR - tabIconSize) / 2,
                    TransportLayout.TAB_STAR_Y + (TransportLayout.TAB_STAR - tabIconSize) / 2,
                    tabIconSize, tabIcon);
        }
        int tabSize = TransportPanelStyle.tabSize(style);
        // 241r：页签文本也支持样式区间（颜色/粗斜下划线）
        TransportText.drawStyled(graphics, Minecraft.getInstance().font,
                TransportPanelStyle.tabText(style), style.getString(TransportPanelStyle.TAB_SPANS),
                TransportLayout.TAB_TEXT_X + TransportPanelStyle.tabX(style),
                TransportLayout.TAB_TEXT_Y + 1 - tabSize / 2 + TransportPanelStyle.tabY(style),
                tabSize, TransportPanelStyle.tabColor(style) | 0xFF000000, 0);

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

        // 241r：行按钮与搜索框都是 vanilla 子控件，super.m_88315_ 会再画一次
        // → 这里不再手动画（否则文字/光标重影）

        // 边栏画在 super 之前：CNPC 的输入框/文本区由 super 画，必须压在边栏背景之上
        this.drawSidebar(graphics, mouseX, mouseY);
        this.drawHint(graphics);

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
        CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
        CompoundTag style = TransportClientCache.panel();
        String icon = TransportPanelStyle.curIcon(style);
        if (icon.isEmpty()) {
            icon = meta == null ? "" : meta.getString(TransportKeys.ICON);
        }
        int curIconPct = TransportPanelStyle.curIconSize(style);
        int curIconSize = Math.round(TransportLayout.CURRENT_ICON * curIconPct / 100f);
        // 241n：XY 偏移只作用于文本，图标不跟着动
        this.drawIcon(graphics,
                TransportLayout.CURRENT_ICON_X + (TransportLayout.CURRENT_ICON - curIconSize) / 2,
                TransportLayout.CURRENT_ICON_Y + (TransportLayout.CURRENT_ICON - curIconSize) / 2,
                curIconSize, icon);
        graphics.drawString(font, tr("cnpcplus.transporter.current") + "\uff1a",
                TransportLayout.CURRENT_LABEL_X + TransportPanelStyle.curX(style),
                TransportLayout.CURRENT_LABEL_Y + TransportPanelStyle.curY(style),
                0xFF444444, false);
        int curSize = TransportPanelStyle.curSize(style);
        // 241t：当前位置文字用**名字的样式区间**（可局部上色）
        TransportText.drawStyled(graphics, font, this.displayName(this.selectedName),
                meta == null ? "" : meta.getString(TransportKeys.NAME_SPANS),
                TransportLayout.CURRENT_NAME_X + TransportPanelStyle.curX(style),
                TransportLayout.CURRENT_NAME_Y + TransportPanelStyle.curY(style),
                curSize, TransportPanelStyle.curColor(style) | 0xFF000000, 0);
    }

    private void drawImagePanel(GuiGraphics graphics) {
        // 框固定；层级 = 展示板 < 图片 < 图片框。图片按 ImageMode 绘制 + ImgX/Y 平移，并裁剪在框内。
        int[] rect = this.imageFrameRect();
        CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
        if (meta != null) {
            String imgKey = meta.getString(TransportKeys.IMAGE);
            if (imgKey != null && !imgKey.isEmpty()) {
                TransportClientCache.requestImage(imgKey);
                var tex = TransportClientCache.imageId(imgKey);
                if (TransportClientCache.hasImage(imgKey) && tex != null) {
                    // 源尺寸只认客户端纹理缓存（meta 里的 SrcW/SrcH 可能来自旧数据/丢失）
                    int[] cached = TransportClientCache.imageSizeOf(tex);
                    int sw = cached == null ? 0 : cached[0];
                    int sh = cached == null ? 0 : cached[1];
                    int mode = meta.getInt(TransportKeys.IMAGE_MODE);
                    int offX = meta.getInt(TransportKeys.IMG_X);
                    int offY = meta.getInt(TransportKeys.IMG_Y);
                    int cx1 = this.sx(rect[0]);
                    int cy1 = this.sy(rect[1]);
                    int cx2 = cx1 + this.sw(rect[2]);
                    int cy2 = cy1 + this.sh(rect[3]);
                    graphics.enableScissor(cx1, cy1, cx2, cy2);
                    TransportImage.draw(graphics, tex, sw, sh,
                            rect[0], rect[1], rect[2], rect[3], mode, offX, offY);
                    graphics.disableScissor();
                }
            }
        }
        // 图片框画在图片之上（框固定，不随图缩放）
        graphics.blit(TransportAssets.IMAGE_FRAME,
                TransportLayout.IMG_FRAME_X, TransportLayout.IMG_FRAME_Y,
                0f, 0f, TransportLayout.IMG_FRAME_W, TransportLayout.IMG_FRAME_H,
                TransportLayout.IMG_FRAME_W, TransportLayout.IMG_FRAME_H);
    }

    /** 图片框**整块**（设计坐标，固定）。241w：图片按原比例缩放到与框同大小，框再盖在上面（框是带透明内框的边框贴图）。 */
    private int[] imageFrameRect() {
        return new int[]{
                TransportLayout.IMG_FRAME_X,
                TransportLayout.IMG_FRAME_Y,
                TransportLayout.IMG_FRAME_W,
                TransportLayout.IMG_FRAME_H};
    }

    private void drawIntro(GuiGraphics graphics) {
        graphics.blit(TransportAssets.INTRO_PANEL,
                TransportLayout.INTRO_X, TransportLayout.INTRO_Y,
                0f, 0f, TransportLayout.INTRO_W, TransportLayout.INTRO_H,
                TransportLayout.INTRO_W, TransportLayout.INTRO_H);
        CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
        String desc = meta == null ? "" : meta.getString(TransportKeys.DESC);
        Font font = Minecraft.getInstance().font;
        CompoundTag style = TransportClientCache.panel();
        int introSize = TransportPanelStyle.introSize(style);
        int offX = TransportPanelStyle.introX(style);
        int offY = TransportPanelStyle.introY(style);
        if (desc == null || desc.isEmpty()) {
            TransportText.draw(graphics, font, tr("cnpcplus.transporter.editor.noText"),
                    TransportLayout.INTRO_TEXT_X + offX, TransportLayout.INTRO_TEXT_Y + offY,
                    introSize, 0xFF777777);
            return;
        }
        int sc0x = this.sx(TransportLayout.INTRO_TEXT_X + offX);
        int sc0y = this.sy(TransportLayout.INTRO_TEXT_Y + offY);
        int sc1x = sc0x + this.sw(TransportLayout.INTRO_TEXT_W);
        int sc1y = sc0y + this.sh(TransportLayout.INTRO_H - 16);
        graphics.enableScissor(sc0x, sc0y, sc1x, sc1y);
        // 241q：多行 + 样式区间（颜色/粗斜下划线，正文干净）
        TransportText.drawStyled(graphics, font, desc,
                meta == null ? "" : meta.getString(TransportKeys.DESC_SPANS),
                TransportLayout.INTRO_TEXT_X + offX, TransportLayout.INTRO_TEXT_Y + offY,
                introSize, TransportPanelStyle.introColor(style) | 0xFF000000,
                TransportLayout.INTRO_TEXT_W);
        graphics.disableScissor();
    }

    // ================= 边栏 =================

    private boolean isTextElement() { return this.activeElement >= 0 && this.activeElement <= EL_TAB_TEXT; }
    private boolean isIconElement() {
        return this.activeElement == EL_ROW_ICON || this.activeElement == EL_CUR_ICON
                || this.activeElement == EL_TAB_ICON;
    }
    private boolean isRowElement() {
        return this.activeElement == EL_ROW_TEXT || this.activeElement == EL_ROW_ICON;
    }
    private boolean isImageElement() { return this.activeElement == EL_IMAGE; }
    private boolean hasContent() {
        // 241t：传送点文本/当前位置文本也有内容（= 传送点名字，可选区上色）
        return this.activeElement == EL_INTRO || this.activeElement == EL_TAB_TEXT
                || this.activeElement == EL_CUR_TEXT || this.activeElement == EL_ROW_TEXT;
    }

    /** 边栏内容总高度（用于滚动范围）。 */
    // 边栏布局：**绘制与点击必须用同一份坐标**（241o：两边各算各的 → 偏移 6~20px，
    // 点上面的图标却选中了下面那个；图片模式按钮同理 → 模式根本没切换）
    private static final int SB_SIZE = 0;
    private static final int SB_ICON_PCT = 1;
    private static final int SB_XY = 2;
    private static final int SB_NAME = 3;
    private static final int SB_DELETE = 4;
    private static final int SB_ZOOM = 5;
    private static final int SB_CONTENT = 6;
    private static final int SB_FMT = 7;
    private static final int SB_PALETTE = 8;
    private static final int SB_GRID = 9;
    private static final int SB_MODE = 10;
    private static final int SB_TOTAL = 11;

    private int[] sbLayout() {
        int[] p = new int[SB_TOTAL + 1];
        int y = this.sbContentY();
        p[SB_SIZE] = y;
        if (this.isTextElement()) y += 20;
        p[SB_ICON_PCT] = y;
        if (this.isIconElement()) y += 20;
        p[SB_XY] = y;
        y += 20;
        p[SB_NAME] = y;
        if (this.isRowElement()) {
            y += 20;
            p[SB_DELETE] = y;
            y += 20;
        } else {
            p[SB_DELETE] = -1;
        }
        p[SB_ZOOM] = y;
        p[SB_CONTENT] = y;
        if (this.hasContent()) {
            y += 90;
            p[SB_FMT] = y;
            y += ROW_H + 4;
        } else {
            p[SB_FMT] = -1;
        }
        p[SB_PALETTE] = y;
        if (this.isTextElement()) y += 50;
        p[SB_GRID] = y;
        if (this.isIconElement()) y += this.iconGridHeight();
        p[SB_MODE] = y;
        if (this.isImageElement()) y += 3 * 20 + 30;
        p[SB_TOTAL] = y - this.sbContentY();
        return p;
    }

    private int sbContentH() {
        if (this.activeElement < 0) return 0;
        return this.sbLayout()[SB_TOTAL];
    }

    private int iconGridHeight() {
        List<String> names = TransportAssets.iconNames();
        int total = names.size() + 1;
        int cols = Math.max(1, (this.sbW() - 24) / CELL);
        int rows = (total + cols - 1) / cols;
        // 241q：普通/选中 切换按钮占一行
        return rows * CELL + (this.activeElement == EL_ROW_ICON ? ROW_H + 4 : 0) + 8;
    }

    private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.activeElement < 0) return;
        int w = this.sbW();
        int h = this.sbH();
        Font font = Minecraft.getInstance().font;

        graphics.fill(0, 0, w, h, 0xE0101018);
        drawFrame(graphics, 0, 0, w, h, 0xFF505068);

        // 固定标题行
        String header = tr("cnpcplus.transporter.editor.title") + " " + tr(ELEM_LABEL[this.activeElement]);
        float hs = TransportText.fit(font, header, w - 60);
        TransportText.draw(graphics, font, header, 4, 5, hs, 0xFFFFD700, false);
        int closeX = w - 48;
        boolean hoverClose = inRect(mouseX, mouseY, closeX, 3, 44, ROW_H);
        graphics.fill(closeX, 3, closeX + 44, 3 + ROW_H, hoverClose ? 0xC83A5F8A : 0x60000000);
        drawFrame(graphics, closeX, 3, 44, ROW_H, 0xFF505068);
        TransportText.draw(graphics, font, tr("cnpcplus.transporter.editor.close"), closeX + 10, 5, 1f, 0xFFFFFFFF, false);

        // 可滚动内容区
        int contentH = this.sbContentH();
        int maxScroll = Math.max(0, contentH - (h - this.sbHeadH() - 4));
        if (this.sbScroll > maxScroll) this.sbScroll = maxScroll;
        if (this.sbScroll < 0) this.sbScroll = 0;

        graphics.enableScissor(0, this.sbHeadH(), w, h);
        int[] p = this.sbLayout();
        int half = (w - 16) / 2;

        if (this.isTextElement()) {
            int y = p[SB_SIZE];
            graphics.drawString(font, tr("cnpcplus.transporter.editor.size"), 4, y + 3, 0xFFCCCCCC, false);
            this.place(FIELD_SIZE_ID, 30, y + 1, this.sbFieldW());
        }
        if (this.isIconElement()) {
            int y = p[SB_ICON_PCT];
            graphics.drawString(font, tr("cnpcplus.transporter.editor.iconPct"), half, y + 3, 0xFFCCCCCC, false);
            this.place(FIELD_ICON_PCT_ID, half + 30, y + 1, this.sbFieldW());
        }
        int yxy = p[SB_XY];
        graphics.drawString(font, "X", 4, yxy + 3, 0xFFCCCCCC, false);
        this.place(FIELD_X_ID, 20, yxy + 1, this.sbFieldW());
        graphics.drawString(font, "Y", half, yxy + 3, 0xFFCCCCCC, false);
        this.place(FIELD_Y_ID, half + 20, yxy + 1, this.sbFieldW());

        if (this.isRowElement()) {
            int y = p[SB_NAME];
            graphics.drawString(font, tr("cnpcplus.transporter.editor.name"), 4, y + 3, 0xFFCCCCCC, false);
            // 241n：删除遗留条目
            int dy = p[SB_DELETE];
            boolean own = this.selectedName.equals(this.ownKey);
            int dx = 4;
            int dw = this.sbW() - 16;
            boolean hover = !own && inRect(mouseX, mouseY, dx, dy, dw, ROW_H);
            graphics.fill(dx, dy, dx + dw, dy + ROW_H, own ? 0x40000000 : (hover ? 0xC84A2020 : 0x60000000));
            drawFrame(graphics, dx, dy, dw, ROW_H, own ? 0xFF404058 : 0xFF804040);
            TransportText.draw(graphics, font, own ? tr("cnpcplus.transporter.editor.deleteOwn") : tr("cnpcplus.transporter.editor.delete"),
                    dx + 6, dy + 4, 1f, own ? 0xFF808080 : 0xFFFF6060, false);
        }

        if (this.hasContent()) {
            GuiTextArea area = this.textArea();
            if (area != null) {
                area.x = 4;
                area.y = p[SB_CONTENT];
                area.visible = true;
            }
            int btnY = p[SB_FMT];
            for (int i = 0; i < FMT_LABEL.length; i++) {
                int bx = 4 + i * 26;
                boolean hover = inRect(mouseX, mouseY, bx, btnY, 24, ROW_H);
                graphics.fill(bx, btnY, bx + 24, btnY + ROW_H, hover ? 0xC83A5F8A : 0x60000000);
                drawFrame(graphics, bx, btnY, 24, ROW_H, 0xFF505068);
                TransportText.draw(graphics, font, tr(FMT_LABEL[i]), bx + 8, btnY + 4, 1f, 0xFFFFD700, false);
            }
        }

        if (this.isTextElement()) {
            int y = p[SB_PALETTE];
            graphics.drawString(font, tr("cnpcplus.transporter.editor.color"), 4, y + 2, 0xFF88CCFF, false);
            int cur = this.currentColor();
            int py = y + 14;
            for (int i = 0; i < PALETTE.length; i++) {
                int cx = 4 + (i % SWATCH_COLS) * (SWATCH + 2);
                int cy = py + (i / SWATCH_COLS) * (SWATCH + 2);
                graphics.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0xFF000000 | PALETTE[i]);
                drawFrame(graphics, cx, cy, SWATCH, SWATCH, PALETTE[i] == cur ? 0xFFFFD700 : 0xFF404058);
            }
        }

        if (this.isIconElement()) {
            int y = p[SB_GRID];
            this.drawIconGrid(graphics, mouseX, mouseY, y);
        }

        if (this.isImageElement()) {
            int y = p[SB_MODE];
            graphics.drawString(font, tr("cnpcplus.transporter.editor.imageMode"),
                    4, y + 2, 0xFF88CCFF, false);
            int by = y + 14;
            int bw = this.imageModeBtnW();
            int cur = this.currentImageMode();
            for (int i = 0; i < IMG_MODE_VALUE.length; i++) {
                int bx = 4 + i * (bw + 4);
                boolean sel = IMG_MODE_VALUE[i] == cur;
                boolean hover = inRect(mouseX, mouseY, bx, by, bw, ROW_H);
                graphics.fill(bx, by, bx + bw, by + ROW_H,
                        sel ? 0xC8C8841E : (hover ? 0xC83A5F8A : 0x60000000));
                drawFrame(graphics, bx, by, bw, ROW_H, sel ? 0xFFFFD700 : 0xFF505068);
                TransportText.draw(graphics, font, tr(IMG_MODE_LABEL[i]),
                        bx + 4, by + 4, 1f, sel ? 0xFFFFD700 : 0xFFFFFFFF, false);
            }
            // 拖动框改尺寸早已删除，只保留拖 PNG 导入提示
            TransportText.draw(graphics, font, tr("cnpcplus.transporter.editor.dragPng"),
                    4, by + ROW_H + 8, 1f, 0xFFAAAAAA, false);
        }
        graphics.disableScissor();

        // 边栏滚动条
        if (maxScroll > 0) {
            int trackX = w - 6;
            int trackH = h - this.sbHeadH() - 4;
            graphics.fill(trackX, this.sbHeadH(), trackX + 4, this.sbHeadH() + trackH, 0x60000000);
            int thumbH = Math.max(20, trackH * (h - this.sbHeadH() - 4) / contentH);
            int thumbY = this.sbHeadH() + (maxScroll == 0 ? 0
                    : (trackH - thumbH) * this.sbScroll / maxScroll);
            graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xFF9090A8);
        }
    }

    private void drawIconGrid(GuiGraphics graphics, int mouseX, int mouseY, int y) {
        Font font = Minecraft.getInstance().font;
        List<String> names = TransportAssets.iconNames();
        int total = names.size() + 1;
        int gx = 4;
        int gw = this.sbW() - 24;
        int cols = Math.max(1, gw / CELL);
        int colW = gw / cols;
        boolean dual = this.activeElement == EL_ROW_ICON;
        // 241q：**普通 / 选中 用两个按钮切换，只显示一个图标库**（之前两库并排 → 分不清在改哪个）
        if (dual) {
            int bw = (this.sbW() - 16) / 2 - 2;
            for (int i = 0; i < 2; i++) {
                int bx = 4 + i * (bw + 4);
                boolean hover = inRect(mouseX, mouseY, bx, y, bw, ROW_H);
                graphics.fill(bx, y, bx + bw, y + ROW_H,
                        this.iconSelMode == i ? 0xC8C8841E : (hover ? 0xC83A5F8A : 0x60000000));
                drawFrame(graphics, bx, y, bw, ROW_H, this.iconSelMode == i ? 0xFFFFD700 : 0xFF505068);
                TransportText.draw(graphics, font, tr(i == 0
                                ? "cnpcplus.transporter.editor.iconNormal" : "cnpcplus.transporter.editor.iconSelected"),
                        bx + 4, y + 4, 1f, this.iconSelMode == i ? 0xFFFFD700 : 0xFFFFFFFF, false);
            }
            y += ROW_H + 4;
        }
        this.drawGrid(graphics, mouseX, mouseY, gx, y, gw, cols, colW, names, total, dual && this.iconSelMode == 1);
    }

    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY, int gx, int gy, int gw,
                          int cols, int colW, List<String> names, int total, boolean selectedSet) {
        Font font = Minecraft.getInstance().font;
        String current = this.currentIcon(selectedSet);
        for (int idx = 0; idx < total; idx++) {
            int row = idx / cols;
            int col = idx % cols;
            int cy = gy + row * CELL;
            int cx = gx + col * colW;
            String name = idx == 0 ? "" : names.get(idx - 1);
            boolean selected = name.equals(current);
            boolean hover = inRect(mouseX, mouseY, cx, cy, colW - 2, CELL - 2);
            graphics.fill(cx, cy, cx + colW - 2, cy + CELL - 2,
                    selected ? 0xC8C8841E : (hover ? 0x803A5F8A : 0x60000000));
            drawFrame(graphics, cx, cy, colW - 2, CELL - 2,
                    selected ? 0xFFFFD700 : 0xFF404058);
            if (idx == 0) {
                String def = tr("cnpcplus.transporter.editor.default");
                float s = TransportText.fit(font, def, colW - 8);
                TransportText.draw(graphics, font, def,
                        cx + (colW - 2 - TransportText.width(font, def, s)) / 2,
                        cy + (CELL - 2 - Math.round(9f * s)) / 2, s, 0xFFCCCCCC, false);
            } else {
                int iconSize = Math.min(22, Math.max(8, colW - 8));
                this.drawIcon(graphics, cx + (colW - 2 - iconSize) / 2,
                        cy + (CELL - 2 - iconSize) / 2, iconSize, name);
            }
        }
    }

    /** 放置/显示一个边栏输入框（跟随滚动；不属于当前元素的直接隐藏）。 */
    private void place(int id, int x, int y, int w) {
        GuiTextFieldNop f = this.getTextField(id);
        if (f == null) return;
        f.setX(x);
        f.setY(y);
        f.setWidth(w);
        f.visible = y >= this.sbHeadH() && y < this.sbH() - 14;
    }



    /** 底部指引 + 临时提示。 */
    private void drawHint(GuiGraphics graphics) {
        Font font = Minecraft.getInstance().font;
        String guide = tr("cnpcplus.transporter.editor.guide");
        float gs = TransportText.fit(font, guide, this.width - 20);
        TransportText.draw(graphics, font, guide,
                (this.width - TransportText.width(font, guide, gs)) / 2,
                this.height - 12, gs, 0xFF9AA0B4, false);
        if (this.hint.isEmpty() || System.currentTimeMillis() >= this.hintUntil) return;
        float s = TransportText.fit(font, this.hint, this.width - 20);
        TransportText.draw(graphics, font, this.hint,
                (this.width - TransportText.width(font, this.hint, s)) / 2,
                this.height - 24, s, 0xFFFF6060, false);
    }

    /**
     * 命中测试：面板上的可编辑元素。
     */
    private int hitElement(double mx, double my) {
        if (this.activeElement >= 0
                && inRect(mx, my, 0, 0, this.sbW(), this.sbH())) {
            return -1;
        }
        CompoundTag style = TransportClientCache.panel();
        int tabIconPct = TransportPanelStyle.tabIconSize(style);
        int tabIconSize = Math.round(TransportLayout.TAB_STAR * tabIconPct / 100f);
        if (inRect(mx, my,
                this.sx(TransportLayout.TAB_STAR_X + (TransportLayout.TAB_STAR - tabIconSize) / 2),
                this.sy(TransportLayout.TAB_STAR_Y + (TransportLayout.TAB_STAR - tabIconSize) / 2),
                this.sw(tabIconSize), this.sh(tabIconSize))) {
            return EL_TAB_ICON;
        }
        if (this.inText(mx, my, TransportLayout.TAB_TEXT_X + TransportPanelStyle.tabX(style),
                TransportLayout.TAB_TEXT_Y + 1 - TransportPanelStyle.tabSize(style) / 2
                        + TransportPanelStyle.tabY(style),
                TransportPanelStyle.tabText(style), TransportPanelStyle.tabSize(style))) {
            return EL_TAB_TEXT;
        }
        int curIconPct = TransportPanelStyle.curIconSize(style);
        int curIconSize = Math.round(TransportLayout.CURRENT_ICON * curIconPct / 100f);
        if (inRect(mx, my,
                this.sx(TransportLayout.CURRENT_ICON_X + (TransportLayout.CURRENT_ICON - curIconSize) / 2
                        + TransportPanelStyle.curX(style)),
                this.sy(TransportLayout.CURRENT_ICON_Y + (TransportLayout.CURRENT_ICON - curIconSize) / 2
                        + TransportPanelStyle.curY(style)),
                this.sw(curIconSize), this.sh(curIconSize))) {
            return EL_CUR_ICON;
        }
        if (this.inText(mx, my, TransportLayout.CURRENT_NAME_X + TransportPanelStyle.curX(style),
                TransportLayout.CURRENT_NAME_Y + TransportPanelStyle.curY(style),
                this.displayName(this.selectedName), TransportPanelStyle.curSize(style))) {
            return EL_CUR_TEXT;
        }
        if (inRect(mx, my, this.sx(TransportLayout.INTRO_X + TransportPanelStyle.introX(style)),
                this.sy(TransportLayout.INTRO_Y + TransportPanelStyle.introY(style)),
                this.sw(TransportLayout.INTRO_W), this.sh(TransportLayout.INTRO_H))) {
            return EL_INTRO;
        }
        if (inRect(mx, my, this.sx(TransportLayout.IMG_FRAME_X), this.sy(TransportLayout.IMG_FRAME_Y),
                this.sw(TransportLayout.IMG_FRAME_W), this.sh(TransportLayout.IMG_FRAME_H))) {
            return EL_IMAGE;
        }
        return this.hitRow(mx, my);
    }

    /** 行按钮在屏幕空间自绘（图标 x=btn.x+10、文本 x=图标右+10），按同样规则命中。 */
    private int hitRow(double mx, double my) {
        Font font = Minecraft.getInstance().font;
        int rowSize = TransportPanelStyle.rowSize(TransportClientCache.panel());
        for (int i = 0; i < TransportLayout.ROW_VISIBLE; i++) {
            GuiButtonNop btn = this.getButton(TransportLayout.BTN_ROW_ID_BASE + i);
            if (!(btn instanceof GuiTransportRowButton row)) continue;
            if (!row.visible || row.entryName == null || row.entryName.isEmpty()) continue;
            int h = row.getHeight();
            int iconSize = Math.min(40, h - 8);
            int iconX = row.getX() + 10;
            int iconY = row.getY() + (h - iconSize) / 2;
            if (inRect(mx, my, iconX, iconY, iconSize, iconSize)) {
                this.selectRow(row.entryName);
                return EL_ROW_ICON;
            }
            float textScale = (float) row.getWidth() / TransportAssets.ROW_TEX_W * rowSize / 9f;
            int textX = iconX + iconSize + 10;
            int textY = row.getY() + Math.round((h - 9f * textScale) / 2f);
            int textW = Math.max(4, Math.round(font.width(this.displayName(row.entryName)) * textScale));
            int textH = Math.max(8, Math.round(9f * textScale));
            if (inRect(mx, my, textX, textY - 2, textW, textH + 2)) {
                this.selectRow(row.entryName);
                return EL_ROW_TEXT;
            }
        }
        return -1;
    }

    /** 设计空间文本框命中（文字宽=字体宽×字号/9）。 */
    private boolean inText(double mx, double my, int dx, int dy, String text, int size) {
        if (text == null || text.isEmpty()) return false;
        Font font = Minecraft.getInstance().font;
        int w = Math.max(4, Math.round(font.width(text) * (size / 9f))) + 2;
        int h = Math.max(8, size);
        return inRect(mx, my, this.sx(dx), this.sy(dy), this.sw(w), this.sh(h));
    }

    private void selectRow(String name) {
        if (name.equals(this.selectedName)) return;
        this.selectedName = name;
        this.lastClick = name;
        this.lastClickMs = System.currentTimeMillis();
        this.syncFields();
    }

    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) return false;

        if (this.activeElement >= 0 && inRect(mouseX, mouseY, 0, 0, this.sbW(), this.sbH())) {
            int maxScroll = Math.max(0, this.sbContentH() - (this.sbH() - this.sbHeadH() - 4));
            if (maxScroll <= 0) return true;
            this.sbScroll = Math.max(0, Math.min(maxScroll,
                    this.sbScroll + (delta > 0 ? -18 : 18)));
            this.syncFields();
            return true;
        }
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

    @Override
    public boolean m_7933_(int key, int scanCode, int modifiers) {
        // 241o：ESC = **退出编辑器并保存**（用户明确要求）；边栏用「关闭」按钮收起
        return super.m_7933_(key, scanCode, modifiers);
    }

    @Override
    public boolean m_6375_(double mouseX, double mouseY, int button) {
        if (button == 0) {

            // 241q：点击输入框时**主动给 vanilla 焦点**（否则拖选无效：Screen.mouseClicked 没跑到）
            GuiTextFieldNop hit = this.fieldAt(mouseX, mouseY);
            if (hit != null) {
                // 241z-7：输入框和内容框只能有一个吃键盘，否则打字会同时覆盖 X 和正文
                GuiTextArea area = this.textArea();
                if (area != null) {
                    area.active = false;
                    area.clicked = false;
                }
                this.setFocused(hit);
                return super.m_6375_(mouseX, mouseY, button);
            }
            // 241n：先给内容框（GuiTextArea）——它必须拿到点击才能 active，
            // 否则光标/方向键/Home/输入法整批字符全部无效（CNPC 的 GuiWrapper 会先让输入框吃掉点击）
            GuiTextArea area = this.textArea();
            if (area != null && area.visible && area.enabled
                    && inRect(mouseX, mouseY, area.x, area.y, area.width, area.height)) {
                // 241z-7：点正文时清掉输入框焦点，避免两边同时接收字符
                this.setFocused(null);
                GuiTextFieldNop.unfocus();
                area.m_6375_(mouseX, mouseY, button);
                this.areaAnchor = area instanceof TextAreaSelectionAccess acc
                        ? acc.cnpcplus$selectionStart() : -1;
                this.areaDragging = this.areaAnchor >= 0;
                return true;
            }
            if (this.handleSidebarClick(mouseX, mouseY)) return true;
            int hitEl = this.hitElement(mouseX, mouseY);
            if (hitEl >= 0) {
                this.setActiveElement(this.activeElement == hitEl ? -1 : hitEl);
                return true;
            }
        }
        return super.m_6375_(mouseX, mouseY, button);
    }

    /** 找出鼠标位置命中的输入框（边栏字段 + 搜索框），用于给 vanilla 焦点。 */
    private GuiTextFieldNop fieldAt(double mx, double my) {
        for (GuiTextFieldNop f : this.wrapper.textfields.values()) {
            if (f == null || !f.visible || !f.enabled) continue;
            if (inRect(mx, my, f.getX(), f.getY(), f.getWidth(), f.getHeight())) return f;
        }
        return null;
    }

    @Override
    public boolean m_7979_(double x, double y, int button, double dragX, double dragY) {
        // 241q：CNPC 的 GuiWrapper.mouseDragged 不给输入框转发 → 手动转给 vanilla 焦点控件（拖选文字）
        var focused = this.getFocused();
        if (focused != null && focused.mouseDragged(x, y, button, dragX, dragY)) return true;
        // 241z-8：GuiTextArea 自身没有 mouseDragged，不转发就永远只能整段上色
        GuiTextArea area = this.textArea();
        if (button == 0 && this.areaDragging && area != null && area.active
                && area.visible && area.enabled && area instanceof TextAreaSelectionAccess acc) {
            int anchor = this.areaAnchor >= 0 ? this.areaAnchor : acc.cnpcplus$selectionStart();
            int pos = acc.cnpcplus$selectionPos(x, y);
            acc.cnpcplus$select(Math.min(anchor, pos), Math.max(anchor, pos), pos);
            return true;
        }
        return super.m_7979_(x, y, button, dragX, dragY);
    }

    @Override
    public boolean m_6348_(double x, double y, int button) {
        this.areaDragging = false;
        return super.m_6348_(x, y, button);
    }

    private boolean handleSidebarClick(double mouseX, double mouseY) {
        if (this.activeElement < 0) return false;
        if (!inRect(mouseX, mouseY, 0, 0, this.sbW(), this.sbH())) return false;

        int closeX = this.sbW() - 48;
        if (inRect(mouseX, mouseY, closeX, 3, 44, ROW_H)) {
            this.activeElement = -1;
            this.syncFields();
            return true;
        }

        // 241o：**绘制与点击共用同一份布局**（此前两边各算各的 → 偏移 6~20px，点错行/错按钮）
        int[] p = this.sbLayout();

        // 格式按钮（B/U/I/清）
        if (this.hasContent() && p[SB_FMT] >= 0) {
            int btnY = p[SB_FMT];
            for (int i = 0; i < FMT_LABEL.length; i++) {
                int bx = 4 + i * 26;
                if (!inRect(mouseX, mouseY, bx, btnY, 24, ROW_H)) continue;
                this.applyFormatToContent(FMT_FLAG[i]);
                return true;
            }
        }

        if (this.isRowElement() && p[SB_DELETE] >= 0) {
            int dy = p[SB_DELETE];
            int dw = this.sbW() - 16;
            if (inRect(mouseX, mouseY, 4, dy, dw, ROW_H)) {
                this.deleteEntry(this.selectedName);
                return true;
            }
        }

        if (this.isTextElement()) {
            int py = p[SB_PALETTE] + 14;
            for (int i = 0; i < PALETTE.length; i++) {
                int cx = 4 + (i % SWATCH_COLS) * (SWATCH + 2);
                int cy = py + (i / SWATCH_COLS) * (SWATCH + 2);
                if (!inRect(mouseX, mouseY, cx, cy, SWATCH, SWATCH)) continue;
                this.applyColorToContent(PALETTE[i]);
                return true;
            }
        }

        if (this.isIconElement()) {
            List<String> names = TransportAssets.iconNames();
            int total = names.size() + 1;
            int gx = 4;
            int gw = this.sbW() - 24;
            int cols = Math.max(1, gw / CELL);
            int colW = gw / cols;
            boolean dual = this.activeElement == EL_ROW_ICON;
            int gy = p[SB_GRID];
            if (dual) {
                // 241q：普通/选中 切换按钮
                int bw = (this.sbW() - 16) / 2 - 2;
                for (int i = 0; i < 2; i++) {
                    int bx = 4 + i * (bw + 4);
                    if (!inRect(mouseX, mouseY, bx, gy, bw, ROW_H)) continue;
                    this.iconSelMode = i;
                    playClick();
                    return true;
                }
                gy += ROW_H + 4;
            }
            int col = (int) Math.floor((mouseX - gx) / colW);
            int row = (int) Math.floor((mouseY - gy) / CELL);
            // 241n：不在网格内就不要吞掉这次点击（否则输入框点不到、光标出不来）
            if (col < 0 || col >= cols || row < 0) return false;
            int idx = row * cols + col;
            if (idx < 0 || idx > names.size()) return false;
            if (this.activeElement == EL_ROW_ICON
                    && TransportClientCache.editMeta(this.selectedName) == null) {
                this.setHint(tr("cnpcplus.transporter.editor.hint.select"));
                return true;
            }
            this.applyIcon(idx == 0 ? "" : names.get(idx - 1), dual && this.iconSelMode == 1);
            this.commit(true);
            return true;
        }

        if (this.isImageElement()) {
            int by = p[SB_MODE] + 14;
            int bw = this.imageModeBtnW();
            for (int i = 0; i < IMG_MODE_VALUE.length; i++) {
                int bx = 4 + i * (bw + 4);
                if (!inRect(mouseX, mouseY, bx, by, bw, ROW_H)) continue;
                CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
                if (meta == null) return true;
                meta.putInt(TransportKeys.IMAGE_MODE, IMG_MODE_VALUE[i]);
                this.commit(true);
                return true;
            }
        }
        return false;
    }

    private void setActiveElement(int i) {
        this.activeElement = i;
        this.sbScroll = 0;
        // 241q：切换元素时清掉所有焦点（否则多个输入框同时显示光标）
        this.setFocused(null);
        GuiTextFieldNop.unfocus();
        this.syncFields();
    }

    private void syncFields() {
        GuiTextFieldNop size = this.getTextField(FIELD_SIZE_ID);
        GuiTextFieldNop fx = this.getTextField(FIELD_X_ID);
        GuiTextFieldNop fy = this.getTextField(FIELD_Y_ID);
        if (size == null || fx == null || fy == null) return;
        boolean textElement = this.isTextElement();
        boolean iconElement = this.isIconElement();
        boolean rowElement = this.isRowElement();
        boolean imageElement = this.isImageElement();
        this.syncing = true;
        size.enabled = textElement;
        GuiTextFieldNop iconPct = this.getTextField(FIELD_ICON_PCT_ID);
        if (iconPct != null) iconPct.enabled = iconElement;
        fx.enabled = this.activeElement >= 0;
        fy.enabled = this.activeElement >= 0;
        if (textElement) size.setValue(String.valueOf(this.currentSize()));
        if (iconElement && iconPct != null) iconPct.setValue(String.valueOf(this.currentIconPct()));
        if (this.activeElement >= 0) {
            fx.setValue(String.valueOf(this.currentOffset(true)));
            fy.setValue(String.valueOf(this.currentOffset(false)));
        }
        GuiTextArea area = this.textArea();
        if (area != null) {
            area.visible = this.hasContent();
            area.enabled = area.visible;
            String key = this.selectedName + "#" + this.activeElement;
            boolean switched = !key.equals(this.areaKey);
            this.areaKey = key;
            if (area.visible) {
                String next = this.currentContent();
                if (switched || !next.equals(area.getText())) {
                    area.setText(next);
                    if (area instanceof TextAreaSelectionAccess acc) acc.cnpcplus$clearSelection();
                    this.areaAnchor = -1;
                }
            }
        }
        // 241n：不要在这里 unfocus()——会把用户正在输入的光标清掉（曾导致"点输入框没光标"）
        this.syncing = false;
    }

    private GuiTextArea textArea() {
        for (IGui comp : this.wrapper.components) {
            if (comp instanceof GuiTextArea area && area.id == AREA_CONTENT_ID) return area;
        }
        return null;
    }

    private int currentSize() {
        CompoundTag panel = TransportClientCache.panel();
        if (this.activeElement == EL_ROW_TEXT) {
            // 241n：条目自己的字号（%），换算成实际像素显示
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            int pct = meta == null ? -1 : meta.getInt(TransportKeys.TEXT_SIZE);
            int base = TransportPanelStyle.rowSize(panel);
            if (pct > 0) {
                return Math.max(TransportPanelStyle.MIN_SIZE,
                        Math.min(TransportPanelStyle.MAX_SIZE, Math.round(base * pct / 100f)));
            }
            return base;
        }
        return switch (this.activeElement) {
            case EL_CUR_TEXT -> TransportPanelStyle.curSize(panel);
            case EL_INTRO -> TransportPanelStyle.introSize(panel);
            case EL_TAB_TEXT -> TransportPanelStyle.tabSize(panel);
            default -> TransportPanelStyle.DEFAULT_ROW_SIZE;
        };
    }

    private int currentColor() {
        CompoundTag panel = TransportClientCache.panel();
        CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
        int base;
        String spans;
        if (this.activeElement == EL_ROW_TEXT) {
            int c = meta == null ? -1 : meta.getInt(TransportKeys.TEXT_COLOR);
            base = c >= 0 ? (c & 0xFFFFFF) : TransportPanelStyle.rowColor(panel);
            spans = meta == null ? "" : meta.getString(TransportKeys.NAME_SPANS);
        } else {
            switch (this.activeElement) {
                case EL_CUR_TEXT -> {
                    base = TransportPanelStyle.curColor(panel);
                    spans = meta == null ? "" : meta.getString(TransportKeys.NAME_SPANS);
                }
                case EL_INTRO -> {
                    base = TransportPanelStyle.introColor(panel);
                    spans = meta == null ? "" : meta.getString(TransportKeys.DESC_SPANS);
                }
                case EL_TAB_TEXT -> {
                    base = TransportPanelStyle.tabColor(panel);
                    spans = panel.getString(TransportPanelStyle.TAB_SPANS);
                }
                default -> {
                    base = 0xFFFFFF;
                    spans = "";
                }
            }
        }
        if (!this.isTextElement()) return base & 0xFFFFFF;
        int len = this.contentLength();
        if (len <= 0) return base & 0xFFFFFF;
        int index;
        int[] sel = this.selectionRange();
        if (sel != null) {
            index = sel[0];
        } else {
            GuiTextArea area = this.textArea();
            int before = area == null ? 0 : area.getSelectionBeforeText().length();
            if (this.activeElement == EL_CUR_TEXT) before -= this.currentPrefixLen();
            index = Math.max(0, Math.min(before, len - 1));
        }
        return top.cnpcplus.transport.TransportSpans.colorAt(
                top.cnpcplus.transport.TransportSpans.parse(spans), index, base) & 0xFFFFFF;
    }

    private int currentOffset(boolean xAxis) {
        CompoundTag panel = TransportClientCache.panel();
        return switch (this.activeElement) {
            case EL_ROW_TEXT, EL_ROW_ICON -> xAxis
                    ? TransportPanelStyle.rowX(panel) : TransportPanelStyle.rowY(panel);
            case EL_CUR_TEXT, EL_CUR_ICON -> xAxis
                    ? TransportPanelStyle.curX(panel) : TransportPanelStyle.curY(panel);
            case EL_INTRO -> xAxis
                    ? TransportPanelStyle.introX(panel) : TransportPanelStyle.introY(panel);
            case EL_TAB_TEXT, EL_TAB_ICON -> xAxis
                    ? TransportPanelStyle.tabX(panel) : TransportPanelStyle.tabY(panel);
            case EL_IMAGE -> {
                CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
                yield meta == null ? 0
                        : meta.getInt(xAxis ? TransportKeys.IMG_X : TransportKeys.IMG_Y);
            }
            default -> 0;
        };
    }

    /** 当前图片显示模式（旧数据 MODE_FILL = 适应）。 */
    private int currentImageMode() {
        CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
        int mode = meta == null ? TransportKeys.MODE_FIT : meta.getInt(TransportKeys.IMAGE_MODE);
        return mode == TransportKeys.MODE_FILL ? TransportKeys.MODE_FIT : mode;
    }

    private int currentIconPct() {
        if (this.activeElement == EL_ROW_ICON) {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            int v = meta == null ? 100 : meta.getInt(TransportKeys.ICON_SIZE);
            return v <= 0 ? 100 : v;
        }
        CompoundTag panel = TransportClientCache.panel();
        return this.activeElement == EL_CUR_ICON
                ? TransportPanelStyle.curIconSize(panel) : TransportPanelStyle.tabIconSize(panel);
    }

    private String currentContent() {
        if (this.activeElement == EL_INTRO) {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            return meta == null ? "" : meta.getString(TransportKeys.DESC);
        }
        if (this.activeElement == EL_TAB_TEXT) {
            return TransportPanelStyle.tabText(TransportClientCache.panel());
        }
        if (this.activeElement == EL_CUR_TEXT) {
            // 241q：当前位置文本 = 固定前缀「当前位置：」+ 传送点名字
            return tr("cnpcplus.transporter.current") + "\uff1a" + this.displayName(this.selectedName);
        }
        if (this.activeElement == EL_ROW_TEXT) {
            // 241t：传送点文本 = 该条目的名字（可拖选上色）
            return this.displayName(this.selectedName);
        }
        return "";
    }

    private int currentPrefixLen() {
        return (tr("cnpcplus.transporter.current") + "\uff1a").length();
    }

    private int contentLength() {
        if (this.activeElement == EL_CUR_TEXT || this.activeElement == EL_ROW_TEXT) {
            return this.displayName(this.selectedName).length();
        }
        return this.currentContent().length();
    }

    /** 241q：颜色/格式应用到**选中的那段**（样式单独存，正文保持干净，不再出现 §x§C§C…）。 */
    private void applyColorToContent(int rgb) {
        if (!this.hasContent()) {
            this.applyColor(rgb);
            return;
        }
        int len = this.contentLength();
        if (len <= 0) {
            this.applyColor(rgb);
            return;
        }
        int[] sel = this.selectionRange();
        if (sel != null) {
            this.applySpans(sel[0], sel[1], rgb, 0, false);
        } else {
            this.applySpans(0, len, rgb, 0, false);
        }
    }

    /** 241q：粗体/下划线/斜体（切换）作用于选区。 */
    private void applyFormatToContent(int flag) {
        if (!this.hasContent()) return;
        int[] sel = this.selectionRange();
        if (sel == null) return;
        this.applySpans(sel[0], sel[1], -1, flag, true);
    }

    /** 取内容框当前选区（无选区返回 null；已换算到样式区间坐标）。 */
    private int[] selectionRange() {
        GuiTextArea area = this.textArea();
        if (area == null) return null;
        String text = area.getText() == null ? "" : area.getText();
        String before = area.getSelectionBeforeText();
        String after = area.getSelectionAfterText();
        if (text.length() <= before.length() + after.length()) return null;
        int start = before.length();
        int end = text.length() - after.length();
        if (this.activeElement == EL_CUR_TEXT) {
            int prefix = this.currentPrefixLen();
            start -= prefix;
            end -= prefix;
        }
        if (this.activeElement == EL_ROW_TEXT || this.activeElement == EL_CUR_TEXT) {
            int len = this.displayName(this.selectedName).length();
            start = Math.max(0, Math.min(start, len));
            end = Math.max(0, Math.min(end, len));
        }
        if (end <= start) return null;
        return new int[]{start, end};
    }

    /** 写样式区间（介绍文本=每条目；传送点/当前位置文字=名字 spans；页签文本=面板级）。 */
    private void applySpans(int start, int end, int rgb, int flags, boolean toggle) {
        if (end <= start) return;
        if (this.activeElement == EL_ROW_TEXT || this.activeElement == EL_CUR_TEXT) {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta == null) return;
            String cur = meta.getString(TransportKeys.NAME_SPANS);
            meta.putString(TransportKeys.NAME_SPANS,
                    top.cnpcplus.transport.TransportSpans.apply(cur, start, end, rgb, flags, toggle));
            this.commit(true);
        } else if (this.activeElement == EL_TAB_TEXT) {
            CompoundTag panel = TransportClientCache.panel();
            String cur = panel.getString(TransportPanelStyle.TAB_SPANS);
            panel.putString(TransportPanelStyle.TAB_SPANS,
                    top.cnpcplus.transport.TransportSpans.apply(cur, start, end, rgb, flags, toggle));
            this.commit(false);
        } else {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta == null) return;
            String cur = meta.getString(TransportKeys.DESC_SPANS);
            meta.putString(TransportKeys.DESC_SPANS,
                    top.cnpcplus.transport.TransportSpans.apply(cur, start, end, rgb, flags, toggle));
            this.commit(true);
        }
        playClick();
    }

    private String currentIcon(boolean selectedSet) {
        if (this.activeElement == EL_ROW_ICON) {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta == null) return "";
            return selectedSet ? meta.getString(TransportKeys.ICON_SEL) : meta.getString(TransportKeys.ICON);
        }
        if (this.activeElement == EL_CUR_ICON) {
            return TransportPanelStyle.curIcon(TransportClientCache.panel());
        }
        if (this.activeElement == EL_TAB_ICON) {
            return TransportPanelStyle.tabIcon(TransportClientCache.panel());
        }
        return "";
    }

    private void applySize(int raw) {
        if (this.activeElement == EL_ROW_TEXT) {
            // 241n：传送点文本字号按条目独立（% 相对面板默认）
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta == null) return;
            int base = TransportPanelStyle.rowSize(TransportClientCache.panel());
            int pct = Math.max(25, Math.min(400, Math.round(raw * 100f / Math.max(1, base))));
            meta.putInt(TransportKeys.TEXT_SIZE, pct);
            return;
        }
        CompoundTag panel = TransportClientCache.panel();
        switch (this.activeElement) {
            case EL_CUR_TEXT -> TransportPanelStyle.putSize(panel, TransportPanelStyle.CUR_SIZE, raw);
            case EL_INTRO -> TransportPanelStyle.putSize(panel, TransportPanelStyle.INTRO_SIZE, raw);
            case EL_TAB_TEXT -> TransportPanelStyle.putSize(panel, TransportPanelStyle.TAB_SIZE, raw);
            default -> { }
        }
    }

    private void applyColor(int rgb) {
        if (this.activeElement == EL_ROW_TEXT) {
            // 241n：传送点文本颜色按条目独立
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta == null) return;
            meta.putInt(TransportKeys.TEXT_COLOR, rgb & 0xFFFFFF);
            return;
        }
        CompoundTag panel = TransportClientCache.panel();
        switch (this.activeElement) {
            case EL_CUR_TEXT -> TransportPanelStyle.putColor(panel, TransportPanelStyle.CUR_COLOR, rgb);
            case EL_INTRO -> TransportPanelStyle.putColor(panel, TransportPanelStyle.INTRO_COLOR, rgb);
            case EL_TAB_TEXT -> TransportPanelStyle.putColor(panel, TransportPanelStyle.TAB_COLOR, rgb);
            default -> { }
        }
    }

    private void applyOffset(boolean xAxis, int v) {
        CompoundTag panel = TransportClientCache.panel();
        switch (this.activeElement) {
            case EL_ROW_TEXT, EL_ROW_ICON -> {
                if (xAxis) TransportPanelStyle.putOffset(panel, TransportPanelStyle.ROW_X, v);
                else TransportPanelStyle.putOffset(panel, TransportPanelStyle.ROW_Y, v);
            }
            case EL_CUR_TEXT, EL_CUR_ICON -> {
                if (xAxis) TransportPanelStyle.putOffset(panel, TransportPanelStyle.CUR_X, v);
                else TransportPanelStyle.putOffset(panel, TransportPanelStyle.CUR_Y, v);
            }
            case EL_INTRO -> {
                if (xAxis) TransportPanelStyle.putOffset(panel, TransportPanelStyle.INTRO_X, v);
                else TransportPanelStyle.putOffset(panel, TransportPanelStyle.INTRO_Y, v);
            }
            case EL_TAB_TEXT, EL_TAB_ICON -> {
                if (xAxis) TransportPanelStyle.putOffset(panel, TransportPanelStyle.TAB_X, v);
                else TransportPanelStyle.putOffset(panel, TransportPanelStyle.TAB_Y, v);
            }
            case EL_IMAGE -> {
                CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
                if (meta != null) meta.putInt(xAxis ? TransportKeys.IMG_X : TransportKeys.IMG_Y, v);
            }
            default -> { }
        }
    }

    private void applyIconPct(int raw) {
        if (this.activeElement == EL_ROW_ICON) {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta == null) return;
            meta.putInt(TransportKeys.ICON_SIZE,
                    Math.max(TransportPanelStyle.MIN_ICON_PCT,
                            Math.min(TransportPanelStyle.MAX_ICON_PCT, raw)));
            return;
        }
        CompoundTag panel = TransportClientCache.panel();
        if (this.activeElement == EL_CUR_ICON) {
            TransportPanelStyle.putIconPct(panel, TransportPanelStyle.CUR_ICON_SIZE, raw);
        } else if (this.activeElement == EL_TAB_ICON) {
            TransportPanelStyle.putIconPct(panel, TransportPanelStyle.TAB_ICON_SIZE, raw);
        }
    }



    private void applyIcon(String name, boolean selectedSet) {
        if (this.activeElement == EL_ROW_ICON) {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta == null) return;
            if (selectedSet) {
                meta.putString(TransportKeys.ICON_SEL, name);
            } else {
                meta.putString(TransportKeys.ICON, name);
                // 241n：选中行显示的是「选中图标」→ 没单独设过时跟着普通图标一起变，
                // 否则用户会以为"改了没反应"（要切别的条目才看得到）
                String sel = meta.getString(TransportKeys.ICON_SEL);
                if (sel == null || sel.isEmpty()) {
                    meta.putString(TransportKeys.ICON_SEL, name);
                }
            }
        } else if (this.activeElement == EL_CUR_ICON) {
            TransportClientCache.panel().putString(TransportPanelStyle.CUR_ICON,
                    TransportKeys.sanitize(name));
        } else if (this.activeElement == EL_TAB_ICON) {
            TransportClientCache.panel().putString(TransportPanelStyle.TAB_ICON,
                    TransportKeys.sanitize(name));
        }
    }

    private void applyContent(String raw) {
        String v = raw == null ? "" : raw;
        if (this.activeElement == EL_INTRO) {
            CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
            if (meta != null) meta.putString(TransportKeys.DESC, v);
        } else if (this.activeElement == EL_TAB_TEXT) {
            TransportClientCache.panel().putString(TransportPanelStyle.TAB_TEXT, v);
        } else if (this.activeElement == EL_CUR_TEXT) {
            // 241q：当前位置文本 = 固定前缀 + 名字 → 去掉前缀后当成改名
            String prefix = tr("cnpcplus.transporter.current") + "\uff1a";
            String name = v.startsWith(prefix) ? v.substring(prefix.length()) : v;
            this.applyName(name.trim());
        } else if (this.activeElement == EL_ROW_TEXT) {
            // 241t：传送点文本 = 名字
            this.applyName(v.trim());
        }
    }

    /** 241l：改名（同步 NPC 自身点位对象 + 父页名称输入框）。 */
    private void applyName(String raw) {
        String key = this.selectedName;
        if (key == null || key.isEmpty()) return;
        String newName = raw.length() > 64 ? raw.substring(0, 64) : raw;
        if (newName.isEmpty() || newName.equals(this.displayName(key))) return;
        this.renames.put(key, newName);
        if (this.location != null && this.location.name.equals(key)) {
            this.location.name = newName;
            this.syncParentName(key, newName);
        }
        this.refreshRows();
    }

    private void syncParentName(String oldName, String newName) {
        try {
            Screen parent = this.wrapper.parent;
            if (!(parent instanceof IGuiInterface p)) return;
            for (GuiTextFieldNop tf : p.getWrapper().textfields.values()) {
                if (oldName.equals(tf.getValue())) tf.setValue(newName);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setHint(String msg) {
        this.hint = msg;
        this.hintUntil = System.currentTimeMillis() + HINT_MS;
    }

    // ================= 241n：即时生效 =================

    /** UI 点击音（让用户知道改动已应用，不再像卡住）。 */
    private static void playClick() {
        try {
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
        } catch (Throwable ignored) {
        }
    }

    /** 记录本地改动（面板级）。 */
    private void markPanelDirty() {
        CompoundTag p = TransportClientCache.panel();
        for (String key : p.getAllKeys()) {
            if (p.contains(key)) this.panelLocal.put(key, p.get(key));
        }
    }

    /** 记录本地改动（条目级）。 */
    private void markEntryDirty(String name) {
        CompoundTag meta = TransportClientCache.editMeta(name);
        if (meta == null) return;
        CompoundTag t = new CompoundTag();
        copyEntry(meta, t);
        this.entryLocal.put(name, t);
    }

    /** 条目可视化字段必须整体复制；漏字段会在 meta 回包后被旧值盖掉。 */
    private static void copyEntry(CompoundTag src, CompoundTag dst) {
        if (src == null || dst == null) return;
        dst.putString(TransportKeys.ICON, src.getString(TransportKeys.ICON));
        dst.putString(TransportKeys.ICON_SEL, src.getString(TransportKeys.ICON_SEL));
        dst.putString(TransportKeys.IMAGE, src.getString(TransportKeys.IMAGE));
        dst.putInt(TransportKeys.IMAGE_MODE, src.getInt(TransportKeys.IMAGE_MODE));
        dst.putInt(TransportKeys.ICON_SIZE, src.getInt(TransportKeys.ICON_SIZE));
        dst.putInt(TransportKeys.IMAGE_ZOOM, src.getInt(TransportKeys.IMAGE_ZOOM));
        dst.putInt(TransportKeys.TEXT_COLOR, src.getInt(TransportKeys.TEXT_COLOR));
        dst.putInt(TransportKeys.TEXT_SIZE, src.getInt(TransportKeys.TEXT_SIZE));
        dst.putString(TransportKeys.DESC, src.getString(TransportKeys.DESC));
        dst.putString(TransportKeys.DESC_SPANS, src.getString(TransportKeys.DESC_SPANS));
        dst.putString(TransportKeys.NAME_SPANS, src.getString(TransportKeys.NAME_SPANS));
        dst.putInt(TransportKeys.SRC_W, src.getInt(TransportKeys.SRC_W));
        dst.putInt(TransportKeys.SRC_H, src.getInt(TransportKeys.SRC_H));
        dst.putInt(TransportKeys.IMG_X, src.getInt(TransportKeys.IMG_X));
        dst.putInt(TransportKeys.IMG_Y, src.getInt(TransportKeys.IMG_Y));
        dst.putInt(TransportKeys.IMG_SCALE, src.getInt(TransportKeys.IMG_SCALE));
    }

    /** meta 响应把缓存整个换掉 → 把本地改动重新盖回去。 */
    private void applyLocal() {
        CompoundTag p = TransportClientCache.panel();
        for (String key : this.panelLocal.getAllKeys()) {
            p.put(key, this.panelLocal.get(key));
        }
        for (Map.Entry<String, CompoundTag> e : this.entryLocal.entrySet()) {
            CompoundTag dst = TransportClientCache.editMeta(e.getKey());
            if (dst == null) continue;
            for (String key : e.getValue().getAllKeys()) {
                dst.put(key, e.getValue().get(key));
            }
        }
    }

    /** 241t：条目的发送键 = **点位 id**（名字会变/可能重名；id 唯一，服务端按 id 匹配）。 */
    private String entryKey(String entryName) {
        CompoundTag src = TransportClientCache.editMeta(entryName);
        if (src == null) return entryName;
        int id = src.getInt("Id");
        return id >= 0 ? String.valueOf(id) : entryName;
    }

    /** 即时发包（disk=false：服务端内存立刻生效；关闭编辑器时再 disk=true 落盘）。 */
    private void liveSend(String entryName) {
        CompoundTag panel = TransportClientCache.panel().copy();
        CompoundTag entries = new CompoundTag();
        if (entryName != null && !entryName.isEmpty()) {
            CompoundTag src = TransportClientCache.editMeta(entryName);
            if (src != null) {
                CompoundTag t = new CompoundTag();
                copyEntry(src, t);
                // 241n：改名现在可以实时下发（服务端按**点位 id** 匹配，不会再出现"只存了第一个字"）
                String newName = this.renames.get(entryName);
                if (newName != null && !newName.isEmpty()) t.putString(TransportKeys.NAME, newName);
                entries.put(this.entryKey(entryName), t);
            }
        }
        TransportPacketHandler.CHANNEL.sendToServer(
                new PacketTransportMetaSave(this.npcId, panel, entries, false));
    }

    /** 一次改动 = 记录本地 + 即时发包 + 播放提示音。 */
    private void commit(boolean entryScope) {
        if (entryScope) {
            this.markEntryDirty(this.selectedName);
            this.liveSend(this.selectedName);
        } else {
            this.markPanelDirty();
            this.liveSend(null);
        }
        this.dirty = true;
        this.edited = true;
        if (entryScope && this.ownKey.equals(this.selectedName)) {
            this.syncLocationVisual();
        }
        playClick();
    }

    /** 把编辑结果同步回父页持有的 TransportLocation，防止父页 save() 用旧值覆盖。 */
    private void syncLocationVisual() {
        if (this.location == null || this.ownKey == null || this.ownKey.isEmpty()) return;
        CompoundTag meta = TransportClientCache.editMeta(this.ownKey);
        if (meta == null) return;
        try {
            TransportVisualMeta m = (TransportVisualMeta) (Object) this.location;
            m.cnpcplus$setIcon(meta.getString(TransportKeys.ICON));
            m.cnpcplus$setIconSel(meta.getString(TransportKeys.ICON_SEL));
            m.cnpcplus$setImage(meta.getString(TransportKeys.IMAGE));
            m.cnpcplus$setImageMode(meta.getInt(TransportKeys.IMAGE_MODE));
            m.cnpcplus$setDesc(meta.getString(TransportKeys.DESC));
            m.cnpcplus$setIconSize(meta.getInt(TransportKeys.ICON_SIZE));
            m.cnpcplus$setImageZoom(meta.getInt(TransportKeys.IMAGE_ZOOM));
            m.cnpcplus$setTextColor(meta.getInt(TransportKeys.TEXT_COLOR));
            m.cnpcplus$setTextSize(meta.getInt(TransportKeys.TEXT_SIZE));
            m.cnpcplus$setImgX(meta.getInt(TransportKeys.IMG_X));
            m.cnpcplus$setImgY(meta.getInt(TransportKeys.IMG_Y));
            m.cnpcplus$setImgScale(meta.getInt(TransportKeys.IMG_SCALE));
            m.cnpcplus$setSrcW(meta.getInt(TransportKeys.SRC_W));
            m.cnpcplus$setSrcH(meta.getInt(TransportKeys.SRC_H));
            m.cnpcplus$setDescSpans(meta.getString(TransportKeys.DESC_SPANS));
            m.cnpcplus$setNameSpans(meta.getString(TransportKeys.NAME_SPANS));
        } catch (Throwable t) {
            top.cnpcplus.transport.TransportLog.error(
                    "[cnpcplus] transport editor sync location FAILED: " + t);
        }
    }

    /** 删除条目（遗留传送点）。 */
    private void deleteEntry(String name) {
        if (name == null || name.isEmpty()) return;
        if (name.equals(this.ownKey)) {
            this.setHint(tr("cnpcplus.transporter.editor.hint.ownNoDelete"));
            playClick();
            return;
        }
        CompoundTag entries = new CompoundTag();
        CompoundTag t = new CompoundTag();
        t.putBoolean(TransportKeys.DELETE, true);
        entries.put(name, t);
        TransportPacketHandler.CHANNEL.sendToServer(
                new PacketTransportMetaSave(this.npcId, new CompoundTag(), entries, true));
        TransportClientCache.editMeta(name);
        Map<String, CompoundTag> all = TransportClientCache.allEditMeta();
        all.remove(name);
        this.entryLocal.remove(name);
        this.renames.remove(name);
        this.applyFilter();
        this.setHint(tr("cnpcplus.transporter.editor.hint.deleted") + " " + name);
        playClick();
    }

    @Override
    public void cnpcplus$onDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) return;
        if (this.selectedName.isEmpty() || TransportClientCache.editMeta(this.selectedName) == null) {
            this.setHint(tr("cnpcplus.transporter.editor.hint.selectDrag"));
            return;
        }
        Path p = paths.get(0);
        String fn = p.getFileName().toString();
        if (!fn.toLowerCase(Locale.ROOT).endsWith(".png")) {
            this.setHint(tr("cnpcplus.transporter.editor.hint.pngOnly"));
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(p);
        } catch (Exception e) {
            this.setHint(tr("cnpcplus.transporter.editor.hint.readFail"));
            return;
        }
        if (bytes.length == 0) {
            this.setHint(tr("cnpcplus.transporter.editor.hint.empty"));
            return;
        }
        if (bytes.length > TransportKeys.MAX_IMAGE_BYTES) {
            this.setHint(tr("cnpcplus.transporter.editor.hint.tooBig"));
            return;
        }
        NativeImage img = null;
        try {
            img = NativeImage.read(new java.io.ByteArrayInputStream(bytes));
            if (img.getWidth() > TransportKeys.MAX_IMAGE_DIM
                    || img.getHeight() > TransportKeys.MAX_IMAGE_DIM) {
                this.setHint(tr("cnpcplus.transporter.editor.hint.tooLarge") + " " + TransportKeys.MAX_IMAGE_DIM);
                return;
            }
        } catch (Throwable t) {
            this.setHint(tr("cnpcplus.transporter.editor.hint.decodeFail"));
            return;
        } finally {
            if (img != null) {
                try { img.close(); } catch (Throwable ignored) { }
            }
        }
        String base = TransportKeys.sanitize(fn.substring(0, fn.length() - 4));
        if (base.isEmpty()) base = "img";
        if (base.length() > 54) base = base.substring(0, 54);
        String key = base + "_" + Integer.toHexString(bytes.hashCode());
        // 241v：**分块上传**（整包 1MB+ 会被网络层丢包 → 服务端收不到文件）
        int chunk = top.cnpcplus.transport.network.PacketTransportImageChunk.chunkSize();
        int seq = 0;
        for (int off = 0; off < bytes.length; off += chunk, seq++) {
            int len = Math.min(chunk, bytes.length - off);
            byte[] part = new byte[len];
            System.arraycopy(bytes, off, part, 0, len);
            TransportPacketHandler.CHANNEL.sendToServer(
                    new top.cnpcplus.transport.network.PacketTransportImageChunk(key, seq, part));
        }
        TransportPacketHandler.CHANNEL.sendToServer(
                new top.cnpcplus.transport.network.PacketTransportImageChunk(key, seq, new byte[0]));
        TransportClientCache.acceptImage(key, bytes);
        CompoundTag meta = TransportClientCache.editMeta(this.selectedName);
        meta.putString(TransportKeys.IMAGE, key);
        // 241q：记录源图像素（客户端/服务端都知道 → 绘制时按真实比例，不再只显示左上角）
        int[] src = TransportClientCache.imageSize(key);
        if (src != null) {
            meta.putInt(TransportKeys.SRC_W, src[0]);
            meta.putInt(TransportKeys.SRC_H, src[1]);
        }
        this.commit(true);
        this.setHint(tr("cnpcplus.transporter.editor.hint.imported") + " " + key);
    }

    @Override
    public void save() {
        TransportDropHandler.clear(this);
        TransportClientCache.setMetaListener(null);
        this.syncLocationVisual();
        if (!this.dirty && !this.edited) return;
        this.dirty = false;
        this.edited = false;
        this.sendMeta(true);
    }

    /** 显式保存按钮：不关闭页面，强制把当前缓存落盘。 */
    private void saveNow() {
        this.syncLocationVisual();
        this.dirty = false;
        this.edited = false;
        this.sendMeta(true);
        playClick();
        this.setHint(tr("cnpcplus.transporter.editor.hint.saving"));
    }

    private void sendMeta(boolean disk) {
        CompoundTag panel = TransportClientCache.panel().copy();
        CompoundTag entries = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : TransportClientCache.allEditMeta().entrySet()) {
            CompoundTag src = e.getValue();
            if (src == null) continue;
            CompoundTag t = new CompoundTag();
            copyEntry(src, t);
            String newName = this.renames.get(e.getKey());
            if (newName != null && !newName.isEmpty()) t.putString(TransportKeys.NAME, newName);
            entries.put(this.entryKey(e.getKey()), t);
        }
        TransportPacketHandler.CHANNEL.sendToServer(
                new PacketTransportMetaSave(this.npcId, panel, entries, disk));
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

    /** 241n：语言键（I18n）。 */
    private static String tr(String key, Object... args) {
        String s = net.minecraft.client.resources.language.I18n.get(key);
        if (s == null || s.isEmpty()) return key;
        if (args.length == 0) return s;
        Object[] a = new Object[args.length];
        for (int i = 0; i < args.length; i++) a[i] = args[i];
        try {
            return String.format(s, a);
        } catch (Exception e) {
            return s;
        }
    }
    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
        return TransportClientCache.editMeta(name);
    }

    @Override
    public String cnpcplus$getDisplay(String name) {
        return this.displayName(name);
    }
}
