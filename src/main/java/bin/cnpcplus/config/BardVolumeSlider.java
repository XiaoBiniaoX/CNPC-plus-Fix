package bin.cnpcplus.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

public class BardVolumeSlider extends GuiButton {
    private float value;
    private boolean dragging;

    public BardVolumeSlider(int id, int x, int y) {
        super(id, x, y, 150, 20, "");
        this.value = CnpcPlusConfig.getBardVolume();
        updateDisplay();
    }

    @Override
    protected int getHoverState(boolean hovered) {
        return 0;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;
        super.drawButton(minecraft, mouseX, mouseY, partialTicks);
        minecraft.getTextureManager().bindTexture(BUTTON_TEXTURES);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        int knobX = this.x + (int) (this.value * (this.width - 8));
        drawTexturedModalRect(knobX, this.y, 0, 66, 4, 20);
        drawTexturedModalRect(knobX + 4, this.y, 196, 66, 4, 20);
    }

    @Override
    protected void mouseDragged(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.visible) return;
        if (this.dragging) update(mouseX);
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        if (!super.mousePressed(minecraft, mouseX, mouseY)) return false;
        this.dragging = true;
        update(mouseX);
        return true;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
    }

    private void update(int mouseX) {
        this.value = Math.max(0.0F, Math.min(1.0F, (mouseX - this.x - 4.0F) / (this.width - 8.0F)));
        CnpcPlusConfig.setBardVolume(this.value);
        Minecraft.getMinecraft().getSoundHandler().update();
        updateDisplay();
    }

    private void updateDisplay() {
        this.displayString = I18n.format("soundCategory.bard") + ": " + (int) (this.value * 100.0F) + "%";
    }
}
