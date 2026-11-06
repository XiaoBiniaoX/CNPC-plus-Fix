package bin.cnpcplus.station;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

public final class GrindstoneScreen extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("cnpcplus:textures/gui/grindstone.png");
    public GrindstoneScreen(GrindstoneContainer container) { super(container); }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int x, int y) {
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }
    @Override protected void drawGuiContainerForegroundLayer(int x, int y) {
        fontRenderer.drawString(I18n.format("cnpcplus.station.grindstone"), 8, 6, 4210752);
        fontRenderer.drawString(mc.player.inventory.getDisplayName().getUnformattedText(), 8, 72, 4210752);
    }
    @Override public void drawScreen(int x, int y, float partial) { drawDefaultBackground(); super.drawScreen(x, y, partial); renderHoveredToolTip(x, y); }
}
