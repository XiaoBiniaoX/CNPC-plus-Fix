package top.cnpcplus.transport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import net.minecraft.nbt.CompoundTag;

/**
 * 传送面板行按钮：自绘 row 背景 + 图标 + 文字。
 * 选中=row_bg+IconSel+金字；悬停=绿字；普通=row_normal+Icon+白字。
 */
public class GuiTransportRowButton extends GuiButtonNop {
    public String entryName = "";

    public GuiTransportRowButton(IGuiInterface gui, int id, int x, int y, int w, int h) {
        super(gui, id, x, y, w, h, "");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
        if (meta != null) {
            iconName = selected ? meta.getString("IconSel") : meta.getString("Icon");
        }
        int iconSize = Math.min(40, this.getHeight() - 8);
        int iconX = this.getX() + 10;
        int iconY = this.getY() + (this.getHeight() - iconSize) / 2;
        ResourceLocationHolder.drawIcon(graphics, iconX, iconY, iconSize, iconName);

        int color = selected ? 0xFFFFD700 : (hovered ? 0xFFAAAA00 : 0xFFFFFFFF);
        String text = this.entryName == null ? "" : this.entryName;
        int textX = iconX + iconSize + 10;
        int textY = this.getY() + (this.getHeight() - font.lineHeight) / 2 + 1;
        graphics.drawString(font, text, textX, textY, color, false);
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
