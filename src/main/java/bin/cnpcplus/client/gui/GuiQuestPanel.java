package bin.cnpcplus.client.gui;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.client.config.GuiUtils;
import noppes.npcs.client.gui.util.SubGuiInterface;

/** Native textured panels, fitted as a whole using the 1.21.1 editor's transform. */
abstract class GuiQuestPanel extends SubGuiInterface {
    protected float panelScale = 1;
    private float offsetX, offsetY;

    GuiQuestPanel() { drawDefaultBackground = false; }

    @Override public void initGui() {
        super.initGui();
        panelScale = Math.min(1, Math.min((width - 12F) / xSize, (height - 12F) / ySize));
        offsetX = (width - xSize * panelScale) / 2 - guiLeft * panelScale;
        offsetY = (height - ySize * panelScale) / 2 - guiTop * panelScale;
    }

    protected final int panelX(int x) { return (int)Math.floor((x - offsetX) / panelScale); }
    protected final int panelY(int y) { return (int)Math.floor((y - offsetY) / panelScale); }

    @Override public final void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (hasSubGui()) {
            getSubGui().drawScreen(mouseX, mouseY, partialTicks);
            return;
        }
        GlStateManager.disableDepth();
        drawRect(0, 0, width, height, 0x99000000);
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        GlStateManager.scale(panelScale, panelScale, 1);
        GuiUtils.drawContinuousTexturedBox(getResource("menubg.png"), guiLeft, guiTop,
                0, 0, xSize, ySize, 256, 217, 4, zLevel);
        super.drawScreen(panelX(mouseX), panelY(mouseY), partialTicks);
        drawPanelContents(panelX(mouseX), panelY(mouseY), partialTicks);
        drawPanelOverlay(panelX(mouseX), panelY(mouseY), partialTicks);
        GlStateManager.popMatrix();
    }

    @Override public void mouseClicked(int x, int y, int button) throws java.io.IOException {
        if (hasSubGui()) {
            if (getSubGui() instanceof GuiQuestPanel) ((GuiQuestPanel)getSubGui()).mouseClicked(x, y, button);
            else super.mouseClicked(x, y, button);
            return;
        }
        mouseX = panelX(x);
        mouseY = panelY(y);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void mouseClickMove(int x, int y, int button, long elapsed) {
        if (hasSubGui() && getSubGui() instanceof GuiQuestPanel)
            ((GuiQuestPanel)getSubGui()).mouseClickMove(x, y, button, elapsed);
        else super.mouseClickMove(panelX(x), panelY(y), button, elapsed);
    }

    @Override public void mouseReleased(int x, int y, int button) {
        if (hasSubGui() && getSubGui() instanceof GuiQuestPanel) ((GuiQuestPanel)getSubGui()).mouseReleased(x, y, button);
        else if (hasSubGui()) super.mouseReleased(x, y, button);
        else super.mouseReleased(panelX(x), panelY(y), button);
    }

    protected void drawPanelOverlay(int mouseX, int mouseY, float partialTicks) { }

    protected abstract void drawPanelContents(int mouseX, int mouseY, float partialTicks);
}
