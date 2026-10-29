package top.cnpcplus.transport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import net.minecraft.nbt.CompoundTag;
import top.cnpcplus.transport.TransportKeys;
import top.cnpcplus.transport.TransportPanelStyle;

/**
 * 传送面板行按钮：自绘 row 背景 + 图标 + 文字。
 * 选中=row_bg+IconSel+金字；悬停=绿字；普通=row_normal+Icon+白字。
 * 241l：图标等比尺寸（每点位 IconSize%）、文字颜色与 XY 偏移（面板样式）。
 */
public class GuiTransportRowButton extends GuiButtonNop {
    public String entryName = "";

    public GuiTransportRowButton(IGuiInterface gui, int id, int x, int y, int w, int h) {
        super(gui, id, x, y, w, h, "");
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.shown || !this.visible) return;
        ITransportPanelUI panel = (ITransportPanelUI) this.gui;
        boolean selected = this.entryName != null && this.entryName.equals(panel.cnpcplus$getSelected());
        boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.getWidth()
                && mouseY >= this.getY() && mouseY < this.getY() + this.getHeight();

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.getX(), this.getY(), 0);
        pose.scale((float) this.getWidth() / TransportAssets.ROW_TEX_W,
                (float) this.getHeight() / TransportAssets.ROW_TEX_H, 1f);
        graphics.blit(selected ? TransportAssets.ROW_BG : TransportAssets.ROW_NORMAL,
                0, 0, 0, 0, TransportAssets.ROW_TEX_W, TransportAssets.ROW_TEX_H,
                TransportAssets.ROW_TEX_W, TransportAssets.ROW_TEX_H);
        pose.popPose();

        Font font = net.minecraft.client.Minecraft.getInstance().font;
        CompoundTag meta = panel.cnpcplus$getMeta(this.entryName);
        String iconName = "";
        int iconPct = 100;
        if (meta != null) {
            iconName = selected ? meta.getString(TransportKeys.ICON_SEL) : meta.getString(TransportKeys.ICON);
            iconPct = meta.getInt(TransportKeys.ICON_SIZE);
        }
        if (iconPct < TransportPanelStyle.MIN_ICON_PCT || iconPct > TransportPanelStyle.MAX_ICON_PCT) {
            iconPct = 100;
        }
        CompoundTag style = TransportClientCache.panel();
        int offX = TransportPanelStyle.rowX(style);
        int offY = TransportPanelStyle.rowY(style);

        // 241n：每条目自己的图标尺寸 / 文字颜色 / 文字字号（未设置才用面板默认）
        int textPct = 100;
        int textColor = TransportPanelStyle.rowColor(style);
        if (meta != null) {
            int c = meta.getInt(TransportKeys.TEXT_COLOR);
            if (c >= 0) textColor = c & 0xFFFFFF;
            int s = meta.getInt(TransportKeys.TEXT_SIZE);
            if (s > 0) textPct = s;
        }
        int rowSize = TransportPanelStyle.rowSize(style);
        if (textPct != 100) {
            rowSize = Math.max(TransportPanelStyle.MIN_SIZE,
                    Math.min(TransportPanelStyle.MAX_SIZE,
                            Math.round(rowSize * textPct / 100f)));
        }

        int iconSize = Math.min(40, this.getHeight() - 8);
        iconSize = Math.max(4, Math.round(iconSize * iconPct / 100f));
        int iconX = this.getX() + 10 + offX;
        int iconY = this.getY() + (this.getHeight() - iconSize) / 2 + offY;
        ResourceLocationHolder.drawIcon(graphics, iconX, iconY, iconSize, iconName);

        int color = textColor | 0xFF000000;
        String text = panel.cnpcplus$getDisplay(this.entryName);
        if (text == null) text = "";
        int textX = iconX + iconSize + 10;
        float rowScale = (float) this.getWidth() / TransportAssets.ROW_TEX_W;
        float textScale = rowScale * rowSize / 9f;
        int textY = this.getY() + Math.round((this.getHeight() - 9f * textScale) / 2f) + offY;
        // 241t：名字支持局部样式（颜色/粗斜下划线）
        TransportText.drawStyled(graphics, font, text,
                meta == null ? "" : meta.getString(TransportKeys.NAME_SPANS),
                textX, textY, (int) Math.round(9f * textScale), color, 0);
    }

    /** 简单 holder，避免本类直接 import ResourceLocation 常量细节。 */
    private static final class ResourceLocationHolder {
        static void drawIcon(GuiGraphics g, int x, int y, int size, String name) {
            var rl = TransportAssets.icon(name);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            float s = (float) size / (name == null || name.isEmpty()
                    ? TransportAssets.DEFAULT_ICON_TEX : TransportAssets.ICON_TEX);
            pose.scale(s, s, 1f);
            int tex = (name == null || name.isEmpty())
                    ? TransportAssets.DEFAULT_ICON_TEX : TransportAssets.ICON_TEX;
            g.blit(rl, 0, 0, 0, 0, tex, tex, tex, tex);
            pose.popPose();
        }
    }
}
