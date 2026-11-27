package top.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.util.GuiNPCInterface;

/** A complete opaque panel; menubg.png becomes transparent below pixel 216. */
abstract class GuiQuestPanel extends GuiNPCInterface {
    GuiQuestPanel() {
        drawDefaultBackground = false;
    }

    @Override
    public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (hasSubGui()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 60);
            getSubGui().render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
            return;
        }
        graphics.fill(0, 0, width, height, 0x99000000);
        int x = guiLeft, y = guiTop, right = x + imageWidth, bottom = y + imageHeight;
        graphics.fill(x, y, right, bottom, 0xff202020);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xff555555);
        graphics.fill(x + 1, y + 1, right - 2, bottom - 2, 0xffffffff);
        graphics.fill(x + 3, y + 3, right - 3, bottom - 3, 0xffc6c6c6);
        graphics.fill(x + 4, bottom - 34, right - 4, bottom - 4, 0xffb4b4b4);
        graphics.hLine(x + 4, right - 5, bottom - 35, 0xff999999);
        super.m_88315_(graphics, mouseX, mouseY, partialTick);
    }
}
