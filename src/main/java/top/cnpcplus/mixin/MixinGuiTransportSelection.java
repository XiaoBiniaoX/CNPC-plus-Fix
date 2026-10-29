package top.cnpcplus.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.player.GuiTransportSelection;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketPlayerTransport;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.TransportOrder;
import top.cnpcplus.transport.client.GuiTransportRowButton;
import top.cnpcplus.transport.client.ITransportPanelUI;
import top.cnpcplus.transport.client.TransportAssets;
import top.cnpcplus.transport.client.TransportClientCache;
import top.cnpcplus.transport.client.TransportLayout;

/**
 * 任务241 玩家传送面板改版：整屏按 952×535 设计空间重绘（单击选中看图/介绍、双击传送、搜索、自定义滚动条）。
 *
 * <p>m_7856_ TAIL：隐原生 travel 按钮/滚动/空标题，按屏幕算 scale 后建搜索框与 4 个行按钮，发 MetaRequest。
 * m_88315_ HEAD cancellable：取消原生 176×222 小面板，按设计空间绘制全套背景/内容，再手动渲染行按钮与搜索框。
 * m_6050_/m_6375_/m_7979_：列表滚轮、滚动条拖动与点击跳转。
 * buttonEvent：100-103 单击选中 / 400ms 内再点传送；50 搜索框过滤。
 */
@Mixin(value = GuiTransportSelection.class, remap = false)
public abstract class MixinGuiTransportSelection extends GuiNPCInterface
        implements IGuiData, ITransportPanelUI {

    @Shadow(remap = false) private GuiCustomScrollNop scroll;
    @Shadow(remap = false) protected int imageWidth;
    @Shadow(remap = false) protected int guiLeft;
    @Shadow(remap = false) protected int guiTop;

    @Unique private final List<String> cnpcplus$all = new ArrayList<>();
    @Unique private final List<String> cnpcplus$filtered = new ArrayList<>();
    @Unique private String cnpcplus$selected = "";
    @Unique private int cnpcplus$offset;
    @Unique private String cnpcplus$search = "";
    @Unique private String cnpcplus$lastClick = "";
    @Unique private long cnpcplus$lastClickMs;
    @Unique private float cnpcplus$scale = 1f;
    @Unique private int cnpcplus$originX;
    @Unique private int cnpcplus$originY;
    @Unique private boolean cnpcplus$dragging;
    @Unique private int cnpcplus$dragStartMouseY;
    @Unique private int cnpcplus$dragStartOffset;

    @Unique
    private void cnpcplus$layout() {
        float sx = (float) this.width / TransportLayout.DESIGN_W;
        float sy = (float) this.height / TransportLayout.DESIGN_H;
        float fit = Math.min(sx, sy);
        // 最多占屏 85% 且不放大超过 1:1（952×535 设计空间已近 GUI scale2 全屏，必须封顶）
        this.cnpcplus$scale = Math.max(0.4f, Math.min(fit * 0.85f, 1.0f));
        this.cnpcplus$originX = (int) ((this.width - TransportLayout.DESIGN_W * this.cnpcplus$scale) / 2f);
        this.cnpcplus$originY = (int) ((this.height - TransportLayout.DESIGN_H * this.cnpcplus$scale) / 2f);
    }

    @Unique
    private int cnpcplus$sx(int designX) {
        return this.cnpcplus$originX + Math.round(designX * this.cnpcplus$scale);
    }

    @Unique
    private int cnpcplus$sy(int designY) {
        return this.cnpcplus$originY + Math.round(designY * this.cnpcplus$scale);
    }

    @Unique
    private int cnpcplus$sw(int designW) {
        return Math.max(1, Math.round(designW * this.cnpcplus$scale));
    }

    @Unique
    private int cnpcplus$sh(int designH) {
        return Math.max(1, Math.round(designH * this.cnpcplus$scale));
    }

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$rebuild(CallbackInfo ci) {
        GuiTransportSelection self = (GuiTransportSelection) (Object) this;

        GuiButtonNop nativeTravel = self.getButton(0);
        if (nativeTravel != null) {
            nativeTravel.visible = false;
            nativeTravel.active = false;
        }
        if (this.scroll != null) {
            this.scroll.visible = false;
        }
        self.wrapper.labels.remove(0);

        this.cnpcplus$layout();

        GuiTextFieldNop search = new GuiTextFieldNop(
                TransportLayout.BTN_SEARCH_ID, self,
                this.cnpcplus$sx(TransportLayout.SEARCH_FIELD_X),
                this.cnpcplus$sy(TransportLayout.SEARCH_FIELD_Y),
                this.cnpcplus$sw(TransportLayout.SEARCH_FIELD_W),
                this.cnpcplus$sh(TransportLayout.SEARCH_FIELD_H),
                "");
        search.setBordered(false);
        search.setMaxLength(64);
        final MixinGuiTransportSelection owner = this;
        search.setResponder(s -> {
            owner.cnpcplus$search = s == null ? "" : s.trim();
            owner.cnpcplus$applyFilter();
        });
        self.addTextField(search);

        for (int i = 0; i < TransportLayout.ROW_VISIBLE; i++) {
            int designY = TransportLayout.ROW_Y + i * TransportLayout.ROW_STRIDE;
            self.addButton(new GuiTransportRowButton(
                    self,
                    TransportLayout.BTN_ROW_ID_BASE + i,
                    this.cnpcplus$sx(TransportLayout.ROW_X),
                    this.cnpcplus$sy(designY),
                    this.cnpcplus$sw(TransportLayout.ROW_W),
                    this.cnpcplus$sh(TransportLayout.ROW_H)));
        }

        this.cnpcplus$applyFilter();
        TransportClientCache.setMetaListener(this::cnpcplus$applyFilter);
        TransportClientCache.requestMeta(self.npc.getId());
    }

    @Unique
    private void cnpcplus$applyFilter() {
        this.cnpcplus$filtered.clear();
        this.cnpcplus$all.sort(TransportOrder.comparator(TransportClientCache.panel(),
                TransportClientCache::meta, name -> name));
        String q = top.cnpcplus.transport.client.Pinyin.normalizeQuery(this.cnpcplus$search);
        for (String name : this.cnpcplus$all) {
            if (q.isEmpty()
                    || top.cnpcplus.transport.client.Pinyin.fuzzy(q, name.toLowerCase(Locale.ROOT))
                    || top.cnpcplus.transport.client.Pinyin.fuzzy(q, top.cnpcplus.transport.client.Pinyin.normalizeQuery(top.cnpcplus.transport.client.Pinyin.of(name)))) {
                this.cnpcplus$filtered.add(name);
            }
        }
        int maxOff = Math.max(0, this.cnpcplus$filtered.size() - TransportLayout.ROW_VISIBLE);
        if (this.cnpcplus$offset > maxOff) this.cnpcplus$offset = maxOff;
        if (this.cnpcplus$offset < 0) this.cnpcplus$offset = 0;
        this.cnpcplus$refreshRows();
    }

    @Unique
    private void cnpcplus$refreshRows() {
        GuiTransportSelection self = (GuiTransportSelection) (Object) this;
        for (int i = 0; i < TransportLayout.ROW_VISIBLE; i++) {
            GuiButtonNop btn = self.getButton(TransportLayout.BTN_ROW_ID_BASE + i);
            if (!(btn instanceof GuiTransportRowButton row)) continue;
            int idx = this.cnpcplus$offset + i;
            if (idx < this.cnpcplus$filtered.size()) {
                row.visible = true;
                row.entryName = this.cnpcplus$filtered.get(idx);
            } else {
                row.visible = false;
                row.entryName = "";
            }
        }
    }

    @Inject(method = "m_88315_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ci.cancel();
        GuiTransportSelection self = (GuiTransportSelection) (Object) this;

        if (this.drawDefaultBackground) {
            this.renderBackground(graphics);
        }

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.cnpcplus$originX, this.cnpcplus$originY, 0);
        pose.scale(this.cnpcplus$scale, this.cnpcplus$scale, 1f);

        graphics.blit(TransportAssets.PANEL, 0, 0, 0f, 0f,
                TransportLayout.DESIGN_W, TransportLayout.DESIGN_H,
                TransportLayout.DESIGN_W, TransportLayout.DESIGN_H);

        graphics.blit(TransportAssets.TAB, TransportLayout.TAB_X, TransportLayout.TAB_Y,
                0f, 0f, TransportLayout.TAB_W, TransportLayout.TAB_H,
                TransportLayout.TAB_W, TransportLayout.TAB_H);
        CompoundTag panelStyle = TransportClientCache.panel();
        String tabIcon = top.cnpcplus.transport.TransportPanelStyle.tabIcon(panelStyle);
        int tabIconPct = top.cnpcplus.transport.TransportPanelStyle.tabIconSize(panelStyle);
        int tabIconSize = Math.round(TransportLayout.TAB_STAR * tabIconPct / 100f);
        int tabIconX = TransportLayout.TAB_STAR_X + (TransportLayout.TAB_STAR - tabIconSize) / 2;
        int tabIconY = TransportLayout.TAB_STAR_Y + (TransportLayout.TAB_STAR - tabIconSize) / 2;
        if (tabIcon.isEmpty()) {
            graphics.blit(TransportAssets.TAB_STAR, tabIconX, tabIconY,
                    0f, 0f, tabIconSize, tabIconSize,
                    TransportLayout.TAB_STAR, TransportLayout.TAB_STAR);
        } else {
            this.cnpcplus$drawIcon(graphics, tabIconX, tabIconY, tabIconSize, tabIcon);
        }
        int tabSize = top.cnpcplus.transport.TransportPanelStyle.tabSize(panelStyle);
        // 241r：页签文本也支持样式区间
        top.cnpcplus.transport.client.TransportText.drawStyled(graphics, Minecraft.getInstance().font,
                top.cnpcplus.transport.TransportPanelStyle.tabText(panelStyle),
                panelStyle.getString(top.cnpcplus.transport.TransportPanelStyle.TAB_SPANS),
                TransportLayout.TAB_TEXT_X + top.cnpcplus.transport.TransportPanelStyle.tabX(panelStyle),
                TransportLayout.TAB_TEXT_Y + 1 - tabSize / 2
                        + top.cnpcplus.transport.TransportPanelStyle.tabY(panelStyle),
                tabSize,
                top.cnpcplus.transport.TransportPanelStyle.tabColor(panelStyle) | 0xFF000000, 0);

        graphics.blit(TransportAssets.SEARCH_BOX, TransportLayout.SEARCH_X, TransportLayout.SEARCH_Y,
                0f, 0f, TransportLayout.SEARCH_W, TransportLayout.SEARCH_H,
                TransportLayout.SEARCH_W, TransportLayout.SEARCH_H);
        graphics.blit(TransportAssets.SEARCH_ICON, TransportLayout.SEARCH_ICON_X, TransportLayout.SEARCH_ICON_Y,
                0f, 0f, TransportLayout.SEARCH_ICON, TransportLayout.SEARCH_ICON,
                TransportLayout.SEARCH_ICON, TransportLayout.SEARCH_ICON);

        graphics.blit(TransportAssets.LIST_GUTTER, TransportLayout.LIST_X, TransportLayout.LIST_Y,
                0f, 0f, TransportLayout.LIST_W, TransportLayout.LIST_H,
                TransportLayout.LIST_W, TransportLayout.LIST_H);

        this.cnpcplus$drawScrollbar(graphics);
        this.cnpcplus$drawCurrentPanel(graphics);
        this.cnpcplus$drawImagePanel(graphics);
        this.cnpcplus$drawIntro(graphics);

        pose.popPose();

        for (int i = 0; i < TransportLayout.ROW_VISIBLE; i++) {
            GuiButtonNop btn = self.getButton(TransportLayout.BTN_ROW_ID_BASE + i);
            if (btn != null && btn.visible && btn.shown) {
                btn.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        GuiTextFieldNop tf = self.getTextField(TransportLayout.BTN_SEARCH_ID);
        if (tf != null) {
            tf.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Unique
    private void cnpcplus$drawScrollbar(GuiGraphics graphics) {
        int total = this.cnpcplus$filtered.size();
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
                + (maxOff == 0 ? 0 : travel * this.cnpcplus$offset / maxOff);
        int thumbX = TransportLayout.SCROLL_TRACK_X
                + (TransportLayout.SCROLL_TRACK_W - TransportLayout.SCROLL_THUMB_W) / 2;
        graphics.blit(TransportAssets.SCROLL_THUMB, thumbX, thumbY,
                0f, 0f, TransportLayout.SCROLL_THUMB_W, thumbH,
                TransportLayout.SCROLL_THUMB_W, 280);
    }

    @Unique
    private void cnpcplus$drawCurrentPanel(GuiGraphics graphics) {
        graphics.blit(TransportAssets.CURRENT_PANEL,
                TransportLayout.CURRENT_X, TransportLayout.CURRENT_Y,
                0f, 0f, TransportLayout.CURRENT_W, TransportLayout.CURRENT_H,
                TransportLayout.CURRENT_W, TransportLayout.CURRENT_H);
        Font font = Minecraft.getInstance().font;
        String current = TransportClientCache.currentName();
        CompoundTag meta = TransportClientCache.meta(current);
        CompoundTag style = TransportClientCache.panel();
        String icon = top.cnpcplus.transport.TransportPanelStyle.curIcon(style);
        if (icon.isEmpty()) {
            icon = meta == null ? "" : meta.getString(TransportKeys.ICON);
        }
        int curIconPct = top.cnpcplus.transport.TransportPanelStyle.curIconSize(style);
        int curIconSize = Math.round(TransportLayout.CURRENT_ICON * curIconPct / 100f);
        // 241n：XY 偏移只作用于文本，图标不跟着动
        this.cnpcplus$drawIcon(graphics,
                TransportLayout.CURRENT_ICON_X + (TransportLayout.CURRENT_ICON - curIconSize) / 2,
                TransportLayout.CURRENT_ICON_Y + (TransportLayout.CURRENT_ICON - curIconSize) / 2,
                curIconSize, icon);
        graphics.drawString(font, "当前位置：",
                TransportLayout.CURRENT_LABEL_X + top.cnpcplus.transport.TransportPanelStyle.curX(style),
                TransportLayout.CURRENT_LABEL_Y + top.cnpcplus.transport.TransportPanelStyle.curY(style),
                0xFF444444, false);
        int curSize = top.cnpcplus.transport.TransportPanelStyle.curSize(style);
        // 241t：当前位置文字用名字的样式区间
        top.cnpcplus.transport.client.TransportText.drawStyled(graphics, font,
                current == null ? "" : current,
                meta == null ? "" : meta.getString(TransportKeys.NAME_SPANS),
                TransportLayout.CURRENT_NAME_X + top.cnpcplus.transport.TransportPanelStyle.curX(style),
                TransportLayout.CURRENT_NAME_Y + top.cnpcplus.transport.TransportPanelStyle.curY(style),
                curSize,
                top.cnpcplus.transport.TransportPanelStyle.curColor(style) | 0xFF000000, 0);
    }

    @Unique
    private void cnpcplus$drawImagePanel(GuiGraphics graphics) {
        // 241q：**层级 = 展示板 < 图片 < 图片框**；显示矩形可由玩家拖动调整（每点位 ImgX/Y/W/H）
        String showName = this.cnpcplus$selected;
        if (showName == null || showName.isEmpty()) {
            showName = TransportClientCache.currentName();
        }
        CompoundTag meta = TransportClientCache.meta(showName);
        if (meta == null) {
            CompoundTag root = TransportClientCache.root();
            if (root != null) meta = root.getCompound("CurrentMeta");
        }
        int rx = TransportLayout.IMG_FRAME_X;
        int ry = TransportLayout.IMG_FRAME_Y;
        int rw = TransportLayout.IMG_FRAME_W;
        int rh = TransportLayout.IMG_FRAME_H;
        String imgKey = meta == null ? "" : meta.getString(TransportKeys.IMAGE);
        if (!imgKey.isEmpty()) {
            TransportClientCache.requestImage(imgKey);
            var tex = TransportClientCache.imageId(imgKey);
            if (TransportClientCache.hasImage(imgKey) && tex != null) {
                // 源尺寸只认客户端纹理缓存
                int[] size = TransportClientCache.imageSizeOf(tex);
                int sw = size == null ? 0 : size[0];
                int sh = size == null ? 0 : size[1];
                int mode = meta.getInt(TransportKeys.IMAGE_MODE);
                int offX = meta.getInt(TransportKeys.IMG_X);
                int offY = meta.getInt(TransportKeys.IMG_Y);
                // 设计坐标 → 屏幕坐标（面板整体 translate+scale），按图片框裁剪
                double s = this.cnpcplus$scale;
                int cx1 = (int) Math.floor(this.cnpcplus$originX + rx * s);
                int cy1 = (int) Math.floor(this.cnpcplus$originY + ry * s);
                int cx2 = (int) Math.ceil(this.cnpcplus$originX + (rx + rw) * s);
                int cy2 = (int) Math.ceil(this.cnpcplus$originY + (ry + rh) * s);
                graphics.enableScissor(cx1, cy1, cx2, cy2);
                top.cnpcplus.transport.client.TransportImage.draw(graphics, tex, sw, sh,
                        rx, ry, rw, rh, mode, offX, offY);
                graphics.disableScissor();
            }
        }        // 图片框固定，画在图片之上
        graphics.blit(TransportAssets.IMAGE_FRAME,
                TransportLayout.IMG_FRAME_X, TransportLayout.IMG_FRAME_Y,
                0f, 0f, TransportLayout.IMG_FRAME_W, TransportLayout.IMG_FRAME_H,
                TransportLayout.IMG_FRAME_W, TransportLayout.IMG_FRAME_H);
    }

    @Unique
    private void cnpcplus$drawIcon(GuiGraphics graphics, int designX, int designY, int designSize, String iconName) {
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

    @Unique
    private void cnpcplus$drawIntro(GuiGraphics graphics) {
        graphics.blit(TransportAssets.INTRO_PANEL,
                TransportLayout.INTRO_X, TransportLayout.INTRO_Y,
                0f, 0f, TransportLayout.INTRO_W, TransportLayout.INTRO_H,
                TransportLayout.INTRO_W, TransportLayout.INTRO_H);
        String showName = this.cnpcplus$selected;
        if (showName == null || showName.isEmpty()) {
            showName = TransportClientCache.currentName();
        }
        CompoundTag meta = TransportClientCache.meta(showName);
        String desc = meta == null ? "" : meta.getString(TransportKeys.DESC);
        if (desc.isEmpty() && meta == null) {
            CompoundTag root = TransportClientCache.root();
            if (root != null) desc = root.getCompound("CurrentMeta").getString(TransportKeys.DESC);
        }
        Font font = Minecraft.getInstance().font;
        CompoundTag style = TransportClientCache.panel();
        int introSize = top.cnpcplus.transport.TransportPanelStyle.introSize(style);
        int offX = top.cnpcplus.transport.TransportPanelStyle.introX(style);
        int offY = top.cnpcplus.transport.TransportPanelStyle.introY(style);
        if (desc.isEmpty()) {
            top.cnpcplus.transport.client.TransportText.draw(graphics, font, "无文本填充",
                    TransportLayout.INTRO_TEXT_X + offX, TransportLayout.INTRO_TEXT_Y + offY,
                    introSize, 0xFF777777);
            return;
        }
        int sc0x = this.cnpcplus$sx(TransportLayout.INTRO_TEXT_X + offX);
        int sc0y = this.cnpcplus$sy(TransportLayout.INTRO_TEXT_Y + offY);
        int sc1x = sc0x + this.cnpcplus$sw(TransportLayout.INTRO_TEXT_W);
        int sc1y = sc0y + this.cnpcplus$sh(TransportLayout.INTRO_H - 16);
        graphics.enableScissor(sc0x, sc0y, sc1x, sc1y);
        // 241q：多行 + 样式区间
        top.cnpcplus.transport.client.TransportText.drawStyled(graphics, font, desc,
                meta == null ? "" : meta.getString(TransportKeys.DESC_SPANS),
                TransportLayout.INTRO_TEXT_X + offX, TransportLayout.INTRO_TEXT_Y + offY,
                introSize,
                top.cnpcplus.transport.TransportPanelStyle.introColor(style) | 0xFF000000,
                TransportLayout.INTRO_TEXT_W);
        graphics.disableScissor();
    }

    @Inject(method = "m_6375_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$mouseClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        int total = this.cnpcplus$filtered.size();
        if (total <= TransportLayout.ROW_VISIBLE) return;
        int trackX = this.cnpcplus$sx(TransportLayout.SCROLL_TRACK_X);
        int trackY = this.cnpcplus$sy(TransportLayout.SCROLL_TRACK_Y);
        int trackW = this.cnpcplus$sw(TransportLayout.SCROLL_TRACK_W);
        int trackH = this.cnpcplus$sh(TransportLayout.SCROLL_TRACK_H);
        if (mouseX < trackX || mouseX > trackX + trackW || mouseY < trackY || mouseY > trackY + trackH) {
            return;
        }
        int maxOff = Math.max(0, total - TransportLayout.ROW_VISIBLE);
        int frac = (int) ((mouseY - trackY) / (double) trackH * (maxOff + 1));
        this.cnpcplus$offset = Math.max(0, Math.min(maxOff, frac));
        this.cnpcplus$refreshRows();
        this.cnpcplus$dragging = true;
        this.cnpcplus$dragStartMouseY = (int) mouseY;
        this.cnpcplus$dragStartOffset = this.cnpcplus$offset;
        cir.setReturnValue(true);
    }

    @Inject(method = "m_7979_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$mouseDrag(double x, double y, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (!this.cnpcplus$dragging || button != 0) return;
        int total = this.cnpcplus$filtered.size();
        int maxOff = Math.max(0, total - TransportLayout.ROW_VISIBLE);
        if (maxOff <= 0) return;
        int trackH = this.cnpcplus$sh(TransportLayout.SCROLL_TRACK_H);
        double dy = y - this.cnpcplus$dragStartMouseY;
        int delta = (int) Math.round(dy / Math.max(1, trackH) * maxOff);
        this.cnpcplus$offset = Math.max(0, Math.min(maxOff, this.cnpcplus$dragStartOffset + delta));
        this.cnpcplus$refreshRows();
        cir.setReturnValue(true);
    }

    @Inject(method = "m_6348_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$mouseRelease(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.cnpcplus$dragging) {
            this.cnpcplus$dragging = false;
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean m_6050_(double mouseX, double mouseY, double delta) {
        if (delta == 0.0D) return false;
        int lx = this.cnpcplus$sx(TransportLayout.LIST_X);
        int ly = this.cnpcplus$sy(TransportLayout.LIST_Y);
        int lw = this.cnpcplus$sw(TransportLayout.LIST_W);
        int lh = this.cnpcplus$sh(TransportLayout.LIST_H);
        if (mouseX < lx || mouseX > lx + lw || mouseY < ly || mouseY > ly + lh) return false;
        int maxOff = Math.max(0, this.cnpcplus$filtered.size() - TransportLayout.ROW_VISIBLE);
        if (maxOff <= 0) return false;
        this.cnpcplus$offset = Math.max(0, Math.min(maxOff, this.cnpcplus$offset + (delta > 0 ? -1 : 1)));
        this.cnpcplus$refreshRows();
        return true;
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$buttonEvent(GuiButtonNop button, CallbackInfo ci) {
        if (button.id < TransportLayout.BTN_ROW_ID_BASE
                || button.id >= TransportLayout.BTN_ROW_ID_BASE + TransportLayout.ROW_VISIBLE) {
            return;
        }
        ci.cancel();
        if (!(button instanceof GuiTransportRowButton row)) return;
        String name = row.entryName;
        if (name == null || name.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (name.equals(this.cnpcplus$selected)
                && now - this.cnpcplus$lastClickMs <= TransportLayout.CLICK_MS
                && name.equals(this.cnpcplus$lastClick)) {
            Packets.sendServer(new SPacketPlayerTransport(name));
            ((GuiTransportSelection) (Object) this).close();
            return;
        }
        this.cnpcplus$selected = name;
        this.cnpcplus$lastClick = name;
        this.cnpcplus$lastClickMs = now;
        CompoundTag meta = TransportClientCache.meta(name);
        if (meta != null) {
            String img = meta.getString(TransportKeys.IMAGE);
            if (!img.isEmpty()) TransportClientCache.requestImage(img);
        }
    }

    public void setData(Vector<String> list, Map<String, Integer> data) {
        this.cnpcplus$all.clear();
        if (list != null) this.cnpcplus$all.addAll(list);
        this.cnpcplus$applyFilter();
    }

    public void setSelected(String selected) {
        if (selected != null && this.cnpcplus$all.contains(selected)) {
            this.cnpcplus$selected = selected;
            int idx = this.cnpcplus$filtered.indexOf(selected);
            if (idx >= 0) {
                if (idx < this.cnpcplus$offset) this.cnpcplus$offset = idx;
                if (idx >= this.cnpcplus$offset + TransportLayout.ROW_VISIBLE) {
                    this.cnpcplus$offset = idx - TransportLayout.ROW_VISIBLE + 1;
                }
                this.cnpcplus$refreshRows();
            }
        }
    }

    @Override
    public void setGuiData(CompoundTag compound) {
        TransportClientCache.acceptMeta(compound);
        this.cnpcplus$applyFilter();
    }

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcplus$clearListener(CallbackInfo ci) {
        TransportClientCache.setMetaListener(null);
    }

    @Override
    public String cnpcplus$getSelected() {
        return this.cnpcplus$selected;
    }

    @Override
    public void cnpcplus$setSelected(String name) {
        this.cnpcplus$selected = name == null ? "" : name;
    }

    @Override
    public List<String> cnpcplus$getVisibleNames() {
        return this.cnpcplus$filtered;
    }

    @Override
    public int cnpcplus$getOffset() {
        return this.cnpcplus$offset;
    }

    @Override
    public void cnpcplus$setOffset(int offset) {
        this.cnpcplus$offset = offset;
    }

    @Override
    public CompoundTag cnpcplus$getMeta(String name) {
        return TransportClientCache.meta(name);
    }
}
