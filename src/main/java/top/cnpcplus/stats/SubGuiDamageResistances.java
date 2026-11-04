package top.cnpcplus.stats;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import noppes.npcs.Resistances;
import noppes.npcs.shared.client.gui.components.*;
import noppes.npcs.shared.client.gui.listeners.ISliderListener;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

public final class SubGuiDamageResistances extends GuiBasic implements ISliderListener, ITextfieldListener {
    private final Resistances resistance;
    private int offset;
    private int rows = 7;
    private boolean dragging;
    private static final String[] ICONS = {
        "flint_and_steel", "lightning_rod", "fire_charge", "lava_bucket", "magma_block", "stone", "pig_spawn_egg", "water_bucket",
        "bread", "cactus", "feather", "elytra", "bedrock", "barrier", "potion", "wither_rose", "dragon_breath", "cod_bucket",
        "sweet_berries", "powder_snow_bucket", "pointed_dripstone", "sand", "anvil", "pointed_dripstone", "bee_spawn_egg",
        "zombie_spawn_egg", "wolf_spawn_egg", "iron_sword", "arrow", "trident", "bow", "firework_rocket", "fire_charge",
        "fire_charge", "wither_skeleton_skull", "snowball", "splash_potion", "enchanted_book", "tnt", "tnt", "sculk_shrieker",
        "respawn_anchor", "barrier", "command_block"
    };

    public SubGuiDamageResistances(Resistances resistance) {
        this.resistance = resistance;
        imageWidth = 346;
        imageHeight = 248;
        drawDefaultBackground = false;
        setBackground("menubg.png");
    }

    @Override public void m_7856_() {
        commit();
        rows = Math.max(2, Math.min(7, (height - 104) / 24));
        imageHeight = 68 + rows * 24;
        super.m_7856_();
        guiTop = Math.min((height - imageHeight) / 2 + 18, height - imageHeight - 6);
        row(-1, guiTop + 8);
        for (int n = 0; n < rows && offset + n < DamageResistanceTable.TYPES.length; n++) row(offset + n, guiTop + 38 + n * 24);
        addButton(new GuiButtonNop(this, 66, guiLeft + imageWidth - 94, guiTop + imageHeight - 26, 84, 20, "gui.done"));
    }

    private void row(int index, int y) {
        int id = index + 1;
        String name = index < 0 ? "cnpcplus.resistance.knockback" : "cnpcplus.resistance." + DamageResistanceTable.TYPES[index];
        String label = I18n.get(name);
        addLabel(new GuiLabel(id, font.plainSubstrByWidth(label, 100), guiLeft + 27, y + 5));
        float value = index < 0 ? (resistance.knockback - 1) * 100 : DamageResistanceTable.values(resistance)[index];
        var field = new GuiTextFieldNop(id, this, guiLeft + 132, y, 84, 20, Float.toString(value)) {
            @Override public int getInnerWidth() { return super.getInnerWidth() - 10; }
            @Override public void m_87963_(GuiGraphics graphics, int x, int y, float partial) {
                super.m_87963_(graphics, x, y, partial);
                graphics.drawString(font, "%", getX() + getWidth() - 11, getY() + 6, getTextColor(), false);
            }
        };
        field.floatsOnly = true;
        field.setMinMaxDefault(-DamageResistanceTable.LIMIT, DamageResistanceTable.LIMIT, 0f);
        field.setMaxLength(14);
        addTextField(field);
        var slider = new GuiSliderNop(this, id, guiLeft + 222, y, percent(value), (value + DamageResistanceTable.LIMIT) / (2 * DamageResistanceTable.LIMIT));
        slider.setWidth(104);
        addSlider(slider);
    }

    private void commit() {
        for (var field : java.util.List.copyOf(wrapper.textfields.values())) unFocused(field);
    }

    @Override public void unFocused(GuiTextFieldNop field) {
        if (!field.isFloat() || !Float.isFinite(field.getFloat())) return;
        float value = Math.max(-DamageResistanceTable.LIMIT, Math.min(DamageResistanceTable.LIMIT, field.getFloat()));
        if (field.id == 0) resistance.knockback = 1 + value / 100;
        else DamageResistanceTable.values(resistance)[field.id - 1] = value;
        var slider = getSlider(field.id);
        if (slider != null) {
            slider.sliderValue = (value + DamageResistanceTable.LIMIT) / (2 * DamageResistanceTable.LIMIT);
            slider.setString(percent(value));
        }
    }
    @Override public void mouseDragged(GuiSliderNop slider) {
        float value = Math.round(slider.sliderValue * 2 * DamageResistanceTable.LIMIT - DamageResistanceTable.LIMIT);
        if (slider.id == 0) resistance.knockback = 1 + value / 100;
        else DamageResistanceTable.values(resistance)[slider.id - 1] = value;
        getTextField(slider.id).setValue(Float.toString(value));
        slider.setString(percent(value));
    }
    private static String percent(float value) { return (value == (int)value ? Integer.toString((int)value) : Float.toString(value)) + "%"; }
    @Override public void mouseReleased(GuiSliderNop slider) { mouseDragged(slider); }
    @Override public void mousePressed(GuiSliderNop slider) { mouseDragged(slider); }
    @Override public void buttonEvent(GuiButtonNop button) { if (button.id == 66) close(); }
    @Override public void save() { commit(); }

    @Override public boolean m_6050_(double x, double y, double delta) {
        if (hasSubGui()) return super.m_6050_(x, y, delta);
        commit();
        offset = Math.max(0, Math.min(DamageResistanceTable.TYPES.length - rows, offset - (int)Math.signum(delta)));
        m_7856_();
        return true;
    }
    @Override public boolean m_6375_(double x, double y, int button) {
        if (button == 0 && x >= guiLeft + imageWidth - 17 && x < guiLeft + imageWidth - 4 && y >= guiTop + 38 && y < guiTop + 38 + rows * 24) {
            dragging = true;
            scrollTo(y);
            return true;
        }
        return super.m_6375_(x, y, button);
    }
    @Override public boolean m_7979_(double x, double y, int button, double dx, double dy) {
        if (dragging) { scrollTo(y); return true; }
        return super.m_7979_(x, y, button, dx, dy);
    }
    @Override public boolean m_6348_(double x, double y, int button) { dragging = false; return super.m_6348_(x, y, button); }
    private void scrollTo(double y) {
        commit();
        offset = Math.max(0, Math.min(DamageResistanceTable.TYPES.length - rows,
                (int)Math.round((y - guiTop - 38) / (rows * 24) * (DamageResistanceTable.TYPES.length - rows))));
        m_7856_();
    }

    @Override public void m_88315_(GuiGraphics graphics, int x, int y, float partial) {
        // menubg's visible panel ends at v184, not v256: scale the actual panel, not its black tail.
        renderBackground(graphics);
        var texture = background;
        background = null;
        graphics.blit(texture, guiLeft, guiTop, imageWidth, imageHeight, 0, 0, 256, 184, 256, 256);
        super.m_88315_(graphics, x, y, partial);
        background = texture;
        graphics.renderItem(new ItemStack(Items.IRON_CHESTPLATE), guiLeft + 6, guiTop + 10);
        for (int n = 0; n < rows && offset + n < ICONS.length; n++) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation("minecraft", ICONS[offset + n]));
            graphics.renderItem(new ItemStack(item), guiLeft + 6, guiTop + 40 + n * 24);
        }
        int top = guiTop + 38, track = rows * 24;
        graphics.fill(guiLeft + imageWidth - 15, top, guiLeft + imageWidth - 8, top + track, 0xFF333333);
        int thumb = Math.max(12, track * rows / DamageResistanceTable.TYPES.length);
        int sy = top + offset * (track - thumb) / (DamageResistanceTable.TYPES.length - rows);
        graphics.fill(guiLeft + imageWidth - 15, sy, guiLeft + imageWidth - 8, sy + thumb, 0xFFFFFFFF);
        if (x >= guiLeft + 27 && x < guiLeft + 128) {
            int index = y >= guiTop + 38 ? offset + (y - guiTop - 38) / 24 : -1;
            if ((index == -1 && y >= guiTop + 8 && y < guiTop + 28)
                    || (index >= offset && index < offset + rows && index < DamageResistanceTable.TYPES.length))
                graphics.renderTooltip(font, net.minecraft.network.chat.Component.translatable(index < 0 ? "cnpcplus.resistance.knockback" : "cnpcplus.resistance." + DamageResistanceTable.TYPES[index]), x, y);
        }
    }
}
