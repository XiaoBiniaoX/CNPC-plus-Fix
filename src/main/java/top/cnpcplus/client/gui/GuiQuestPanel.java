package top.cnpcplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.util.GuiNPCInterface;

/** A complete opaque panel; menubg.png becomes transparent below pixel 216. */
abstract class GuiQuestPanel extends GuiNPCInterface {
    private float panelScale = 1;
    private float offsetX, offsetY;

    GuiQuestPanel() {
        drawDefaultBackground = false;
    }

    @Override
    public void init() {
        super.init();
        panelScale = Math.min(1, Math.min((width - 12f) / imageWidth, (height - 12f) / imageHeight));
        offsetX = (width - imageWidth * panelScale) / 2 - guiLeft * panelScale;
        offsetY = (height - imageHeight * panelScale) / 2 - guiTop * panelScale;
    }

    protected final double panelX(double x) { return (x - offsetX) / panelScale; }
    protected final double panelY(double y) { return (y - offsetY) / panelScale; }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (hasSubGui()) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 60);
            getSubGui().render(graphics, mouseX, mouseY, partialTick);
            graphics.pose().popPose();
            return;
        }
        graphics.fill(0, 0, width, height, 0x99000000);
        graphics.pose().pushPose();
        graphics.pose().translate(offsetX, offsetY, 0);
        graphics.pose().scale(panelScale, panelScale, 1);
        renderPanel(graphics, (int)Math.floor(panelX(mouseX)), (int)Math.floor(panelY(mouseY)), partialTick);
        graphics.pose().popPose();
    }

    protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = guiLeft, y = guiTop, right = x + imageWidth, bottom = y + imageHeight;
        graphics.fill(x, y, right, bottom, 0xff202020);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, 0xff555555);
        graphics.fill(x + 1, y + 1, right - 2, bottom - 2, 0xffffffff);
        graphics.fill(x + 3, y + 3, right - 3, bottom - 3, 0xffc6c6c6);
        graphics.fill(x + 4, bottom - 34, right - 4, bottom - 4, 0xffb4b4b4);
        graphics.hLine(x + 4, right - 5, bottom - 35, 0xff999999);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    // Child screens keep real Screen dimensions and receive unscaled input, so nested panels fit independently.
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (hasSubGui()) {
            getSubGui().mouseClicked(x, y, button);
            return true;
        }
        return super.mouseClicked(panelX(x), panelY(y), button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (hasSubGui()) {
            getSubGui().mouseDragged(x, y, button, dx, dy);
            return true;
        }
        return super.mouseDragged(panelX(x), panelY(y), button, dx / panelScale, dy / panelScale);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (hasSubGui()) {
            getSubGui().mouseReleased(x, y, button);
            return true;
        }
        return super.mouseReleased(panelX(x), panelY(y), button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (hasSubGui()) {
            getSubGui().mouseScrolled(x, y, horizontal, vertical);
            return true;
        }
        return super.mouseScrolled(panelX(x), panelY(y), horizontal, vertical);
    }
}
