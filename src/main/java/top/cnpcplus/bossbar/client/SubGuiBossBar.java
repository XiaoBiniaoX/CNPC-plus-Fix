package top.cnpcplus.bossbar.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;
import top.cnpcplus.bossbar.BossBarData;
import top.cnpcplus.bossbar.BossBarSettings;

/** Edits a private draft; the existing display save packet remains the authority. */
public final class SubGuiBossBar extends GuiBasic implements ITextfieldListener {
    private static final int RADIUS = 56;
    private final GuiNpcDisplay parent;
    private int mode, rgb;
    private float hue, saturation, value;
    private boolean updatingFields;
    private int dragging; // 1 = wheel, 2 = brightness
    private ResourceLocation wheel;

    public SubGuiBossBar(GuiNpcDisplay parent) {
        this.parent = parent;
        BossBarSettings settings = BossBarData.get(parent.npc.display);
        mode = settings.mode();
        rgb = settings.rgb();
        fromRgb();
        imageWidth = 296;
        imageHeight = 236;
        setBackground("menubg.png");
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        addButton(new GuiButtonNop(this, 0, guiLeft + 154, guiTop + 34, 128, 20,
                new String[]{"cnpcplus.bossbar.off", "cnpcplus.bossbar.always", "cnpcplus.bossbar.attacking"}, mode));
        String[] names = {"R", "G", "B"};
        for (int i = 0; i < 3; i++) {
            int channel = i, y = guiTop + 72 + i * 23;
            addLabel(new GuiLabel(10 + i, names[i], guiLeft + 154, y + 5));
            GuiTextFieldNop field = new GuiTextFieldNop(10 + i, this, guiLeft + 170, y, 48, 20,
                    Integer.toString((rgb >> (16 - i * 8)) & 255));
            field.setNumbersOnly().setMinMaxDefault(0, 255, (rgb >> (16 - i * 8)) & 255);
            field.setMaxLength(3);
            field.setResponder(text -> editChannel(channel, text));
            addTextField(field);
        }
        addButton(new GuiButtonNop(this, 1, guiLeft + 98, guiTop + 208, 100, 20, "gui.done"));
        if (wheel == null) createWheel();
    }

    private void editChannel(int channel, String text) {
        if (updatingFields) return;
        try {
            int number = Integer.parseInt(text);
            if (number < 0 || number > 255) return;
            int shift = 16 - channel * 8;
            rgb = (rgb & ~(255 << shift)) | (number << shift);
            fromRgb();
        } catch (NumberFormatException ignored) { /* An empty draft must not reset the other channels. */ }
    }

    private void fromRgb() {
        float[] hsv = BossBarSettings.rgbToHsv(rgb);
        // Retain the chosen hue at grey/black so the brightness control is reversible.
        if (hsv[1] > 0) hue = hsv[0];
        if (hsv[2] > 0) saturation = hsv[1];
        value = hsv[2];
    }

    private void fromHsv() {
        rgb = BossBarSettings.hsvToRgb(hue, saturation, value);
        updatingFields = true;
        try {
            for (int i = 0; i < 3; i++) getTextField(10 + i).setValue(Integer.toString((rgb >> (16 - i * 8)) & 255));
        } finally {
            updatingFields = false;
        }
    }

    private void createWheel() {
        int size = RADIUS * 2;
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x + 0.5 - RADIUS, dy = y + 0.5 - RADIUS;
                float distance = (float) Math.hypot(dx, dy);
                float h = (float) (Math.atan2(dy, dx) / (Math.PI * 2));
                int color = BossBarSettings.hsvToRgb(h, distance / RADIUS, 1);
                int alpha = Math.round(Math.max(0, Math.min(1, RADIUS - distance)) * 255);
                // NativeImage stores ABGR, not Java's ARGB.
                image.setPixelRGBA(x, y, alpha << 24 | (color & 255) << 16 | (color & 0xFF00) | (color >> 16));
            }
        }
        wheel = minecraft.getTextureManager().register("cnpcplus_boss_wheel", new DynamicTexture(image));
    }

    @Override
    public boolean m_6375_(double x, double y, int button) {
        if (button == 0) {
            if (Math.hypot(x - (guiLeft + 72), y - (guiTop + 118)) <= RADIUS) dragging = 1;
            else if (x >= guiLeft + 244 && x < guiLeft + 264 && y >= guiTop + 72 && y <= guiTop + 138) dragging = 2;
            if (dragging != 0) {
                pick(x, y);
                return true; // Before GuiWrapper: do not unfocus RGB fields or rebuild controls.
            }
        }
        return super.m_6375_(x, y, button);
    }

    @Override
    public boolean m_7979_(double x, double y, int button, double dx, double dy) {
        if (button == 0 && dragging != 0) {
            pick(x, y);
            return true;
        }
        return super.m_7979_(x, y, button, dx, dy);
    }

    @Override
    public boolean m_6348_(double x, double y, int button) {
        if (button == 0 && dragging != 0) {
            pick(x, y);
            dragging = 0;
            return true;
        }
        return super.m_6348_(x, y, button);
    }

    private void pick(double x, double y) {
        if (dragging == 1) {
            double dx = x - (guiLeft + 72), dy = y - (guiTop + 118);
            hue = (float) (Math.atan2(dy, dx) / (Math.PI * 2));
            saturation = (float) Math.min(1, Math.hypot(dx, dy) / RADIUS);
        } else {
            value = (float) Math.max(0, Math.min(1, 1 - (y - (guiTop + 72)) / 66));
        }
        fromHsv();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        editChannel(field.id - 10, field.getValue());
        field.setValue(Integer.toString((rgb >> (16 - (field.id - 10) * 8)) & 255));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == 0) mode = button.getValue();
        else if (button.id == 1) close();
    }

    @Override
    public void save() {
        BossBarData.put(parent.npc, new BossBarSettings(mode, rgb));
        parent.save();
        if (wheel != null) {
            minecraft.getTextureManager().release(wheel);
            wheel = null;
        }
    }

    @Override
    public void m_88315_(GuiGraphics g, int mx, int my, float partialTicks) {
        super.m_88315_(g, mx, my, partialTicks);
        if (hasSubGui()) return;
        g.drawString(font, I18n.get("cnpcplus.bossbar.title"), guiLeft + 12, guiTop + 12, 0x202020, false);
        g.drawString(font, I18n.get("gui.color"), guiLeft + 16, guiTop + 42, 0x202020, false);
        if (wheel != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(value, value, value, 1);
            g.blit(wheel, guiLeft + 16, guiTop + 62, 0, 0, RADIUS * 2, RADIUS * 2, RADIUS * 2, RADIUS * 2);
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
        double angle = hue * Math.PI * 2;
        int px = guiLeft + 72 + Math.round((float) Math.cos(angle) * saturation * RADIUS);
        int py = guiTop + 118 + Math.round((float) Math.sin(angle) * saturation * RADIUS);
        g.fill(px - 3, py - 3, px + 4, py + 4, 0xFF101010);
        g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
        g.fill(px - 1, py - 1, px + 2, py + 2, 0xFF000000 | rgb);
        g.drawString(font, I18n.get("cnpcplus.bossbar.brightness"), guiLeft + 232, guiTop + 58, 0x202020, false);
        for (int row = 0; row <= 66; row++) {
            g.fill(guiLeft + 246, guiTop + 72 + row, guiLeft + 262, guiTop + 73 + row,
                    0xFF000000 | BossBarSettings.hsvToRgb(hue, saturation, 1 - row / 66f));
        }
        int vy = guiTop + 72 + Math.round((1 - value) * 66);
        g.fill(guiLeft + 243, vy - 1, guiLeft + 265, vy + 2, 0xFFFFFFFF);
        g.drawString(font, String.format("#%06X", rgb), guiLeft + 154, guiTop + 154, 0x202020, false);
        BossBarColors.bar(g, guiLeft + 57, guiTop + 191, rgb, 0.75f);
    }
}
