package bin.cnpcplus.client.gui;

import java.awt.Color;
import java.io.IOException;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import org.lwjgl.input.Mouse;

/** A private RGB draft; mouse capture stays here instead of rebuilding native controls. */
public class SubGuiColorWheel extends SubGuiInterface implements ITextfieldListener {
    private static final int RADIUS = 56;
    private final IntConsumer accept;
    protected int rgb;
    private float hue, saturation, value;
    private int dragging;
    private ResourceLocation wheel;
    public SubGuiColorWheel(int color, IntConsumer accept) {
        this.accept = accept; rgb = color & 0xFFFFFF; fromRgb();
        xSize = 296; ySize = 236; closeOnEsc = true; setBackground("menubg.png");
    }
    @Override public void initGui() {
        super.initGui();
        for (int i = 0; i < 3; i++) {
            int y = guiTop + 72 + i * 23;
            addLabel(new GuiNpcLabel(10 + i, new String[]{"R", "G", "B"}[i], guiLeft + 154, y + 5));
            GuiNpcTextField field = new GuiNpcTextField(10 + i, this, fontRenderer, guiLeft + 170, y, 48, 20,
                    Integer.toString((rgb >> (16 - i * 8)) & 255));
            field.numbersOnly = true; field.setMinMaxDefault(0, 255, (rgb >> (16 - i * 8)) & 255); field.setMaxStringLength(3);
            addTextField(field);
        }
        addButton(new GuiNpcButton(1, guiLeft + 98, guiTop + 208, 100, 20, "gui.done"));
        if (wheel == null) {
            DynamicTexture texture = new DynamicTexture(112, 112);
            int[] pixels = texture.getTextureData();
            for (int y = 0; y < 112; y++) for (int x = 0; x < 112; x++) {
                double dx = x + .5 - RADIUS, dy = y + .5 - RADIUS, distance = Math.hypot(dx, dy);
                int alpha = Math.round((float) Math.max(0, Math.min(1, RADIUS - distance)) * 255);
                pixels[y * 112 + x] = alpha << 24 | Color.HSBtoRGB((float) (Math.atan2(dy, dx) / (Math.PI * 2)),
                        (float) Math.min(1, distance / RADIUS), 1) & 0xFFFFFF;
            }
            texture.updateDynamicTexture(); wheel = mc.getTextureManager().getDynamicTextureLocation("cnpcplus_color_wheel", texture);
        }
    }
    private void fromRgb() {
        float[] hsv = Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
        if (hsv[1] > 0) hue = hsv[0];
        if (hsv[2] > 0) saturation = hsv[1];
        value = hsv[2];
    }
    private void readFields() {
        int next = rgb;
        for (int i = 0; i < 3; i++) {
            String raw = getTextField(10 + i).getText();
            if (raw.isEmpty()) continue;
            int channel;
            try { channel = Integer.parseInt(raw); }
            catch (NumberFormatException ignored) { continue; }
            if (channel < 0 || channel > 255) continue;
            int shift = 16 - i * 8; next = next & ~(255 << shift) | channel << shift;
        }
        if (rgb != next) { rgb = next; fromRgb(); }
    }
    private void pick(int x, int y) {
        if (dragging == 1) {
            double dx = x - guiLeft - 72, dy = y - guiTop - 118;
            hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2)); saturation = (float) Math.min(1, Math.hypot(dx, dy) / RADIUS);
        } else value = Math.max(0, Math.min(1, 1 - (y - guiTop - 72) / 66f));
        rgb = Color.HSBtoRGB(hue, saturation, value) & 0xFFFFFF;
        for (int i = 0; i < 3; i++) getTextField(10 + i).setText(Integer.toString(rgb >> (16 - i * 8) & 255));
    }
    @Override public void mouseClicked(int x, int y, int button) throws IOException {
        if (button == 0) {
            if (Math.hypot(x - guiLeft - 72, y - guiTop - 118) <= RADIUS) dragging = 1;
            else if (x >= guiLeft + 244 && x < guiLeft + 264 && y >= guiTop + 72 && y <= guiTop + 138) dragging = 2;
            if (dragging != 0) { readFields(); pick(x, y); return; }
        }
        super.mouseClicked(x, y, button);
    }
    @Override public void unFocused(GuiNpcTextField field) {
        readFields(); int channel = field.getId() - 10;
        field.setText(Integer.toString(rgb >> (16 - channel * 8) & 255));
    }
    @Override public void buttonEvent(GuiButton button) { if (button.id == 1) close(); }
    @Override public void save() {
        readFields(); if (accept != null) accept.accept(rgb);
        dragging = 0;
        if (wheel != null) { mc.getTextureManager().deleteTexture(wheel); wheel = null; }
    }
    @Override public void drawScreen(int mx, int my, float partial) {
        if (dragging != 0) { pick(mx, my); if (!Mouse.isButtonDown(0)) dragging = 0; }
        else readFields();
        super.drawScreen(mx, my, partial);
        if (hasSubGui()) return;
        drawString(fontRenderer, net.minecraft.client.resources.I18n.format("gui.color"), guiLeft + 12, guiTop + 12, 0xFFFFFF);
        GlStateManager.enableBlend(); GlStateManager.color(value, value, value, 1);
        mc.getTextureManager().bindTexture(wheel);
        drawModalRectWithCustomSizedTexture(guiLeft + 16, guiTop + 62, 0, 0, 112, 112, 112, 112);
        GlStateManager.color(1, 1, 1, 1);
        double angle = hue * Math.PI * 2;
        int px = guiLeft + 72 + Math.round((float) Math.cos(angle) * saturation * RADIUS);
        int py = guiTop + 118 + Math.round((float) Math.sin(angle) * saturation * RADIUS);
        drawRect(px - 3, py - 3, px + 4, py + 4, 0xFF101010);
        drawRect(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
        drawRect(px - 1, py - 1, px + 2, py + 2, 0xFF000000 | rgb);
        drawString(fontRenderer, net.minecraft.client.resources.I18n.format("cnpcplus.bossbar.brightness"), guiLeft + 232, guiTop + 58, 0xFFFFFF);
        for (int y = 0; y <= 66; y++) drawRect(guiLeft + 246, guiTop + 72 + y, guiLeft + 262, guiTop + 73 + y,
                Color.HSBtoRGB(hue, saturation, 1 - y / 66f));
        int vy = guiTop + 72 + Math.round((1 - value) * 66);
        drawRect(guiLeft + 243, vy - 1, guiLeft + 265, vy + 2, 0xFFFFFFFF);
        drawString(fontRenderer, String.format("#%06X", rgb), guiLeft + 154, guiTop + 154, 0xFFFFFF);
        drawRect(guiLeft + 57, guiTop + 184, guiLeft + 239, guiTop + 195, 0xFF000000 | rgb);
    }
}
