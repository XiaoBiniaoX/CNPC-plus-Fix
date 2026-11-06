package bin.cnpcplus.stats;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.Resistances;
import noppes.npcs.client.gui.util.*;
import org.lwjgl.input.Mouse;

/** Port of the 1.20.1 resistance panel: same rows, coordinates, field and scrollbar. */
public final class SubGuiDamageResistances extends SubGuiInterface implements ISliderListener, ITextfieldListener {
    private final Resistances resistance;
    private final List<GuiNpcTextField> fields = new ArrayList<>();
    private int offset, rows = 7;
    private boolean dragging;
    private static final String[] ICONS = {
        "flint_and_steel", "redstone", "fire_charge", "lava_bucket", "magma", "stone", "spawn_egg", "water_bucket",
         "bread", "cactus", "feather", "elytra", "bedrock", "command_block", "potion", "skull", "dragon_breath",
        "sand", "anvil", "spawn_egg", "iron_sword", "arrow", "fire_charge", "snowball", "splash_potion",
        "enchanted_book", "tnt", "tnt", "fireworks"
    };
    public SubGuiDamageResistances(Resistances resistance) {
        this.resistance = resistance; xSize = 346; ySize = 248;
        drawDefaultBackground = false; closeOnEsc = true; setBackground("menubg.png");
    }
    @Override public void initGui() {
        commit(); fields.clear();
        rows = Math.max(2, Math.min(7, (height - 104) / 24)); ySize = 68 + rows * 24;
        offset = Math.max(0, Math.min(DamageResistanceTable.TYPES.length - rows, offset));
        super.func_73866_w_(); guiTop = Math.min((height - ySize) / 2 + 18, height - ySize - 6);
        row(-1, guiTop + 8);
        for (int n = 0; n < rows && offset + n < DamageResistanceTable.TYPES.length; n++) row(offset + n, guiTop + 38 + n * 24);
        addButton(new GuiNpcButton(66, guiLeft + xSize - 94, guiTop + ySize - 26, 84, 20, "gui.done"));
    }
    private void row(int index, int y) {
        int id = index + 1;
        String label = I18n.format(index < 0 ? "cnpcplus.resistance.knockback" : "cnpcplus.resistance." + DamageResistanceTable.TYPES[index]);
        addLabel(new GuiNpcLabel(id, fontRenderer.trimStringToWidth(label, 100), guiLeft + 27, y + 5));
        float value = index < 0 ? (resistance.knockback - 1) * 100 : DamageResistanceTable.values(resistance)[index];
        GuiNpcTextField field = new GuiNpcTextField(id, this, fontRenderer, guiLeft + 132, y, 84, 20, Float.toString(value)) {
            @Override public void drawTextBox(int x, int yy) {
                super.drawTextBox(x, yy); fontRenderer.drawString("%", this.x + this.width - 11, this.y + 6, 0xE0E0E0);
            }
            @Override public int getWidth() { return super.getWidth() - 10; }
        };
        field.setMaxStringLength(14); fields.add(field); addTextField(field);
        GuiNpcSlider slider = new GuiNpcSlider(this, id, guiLeft + 222, y, percent(value), (value + DamageResistanceTable.LIMIT) / (2 * DamageResistanceTable.LIMIT));
        slider.width = 104; addSlider(slider);
    }
    private void commit() { for (GuiNpcTextField field : new ArrayList<>(fields)) unFocused(field); }
    @Override public void unFocused(GuiNpcTextField field) {
        float value;
        try { value = Float.parseFloat(field.getText().trim()); } catch (NumberFormatException error) { return; }
        if (!Float.isFinite(value)) return;
        value = Math.max(-DamageResistanceTable.LIMIT, Math.min(DamageResistanceTable.LIMIT, value));
        int id = field.getId();
        if (id == 0) resistance.knockback = 1 + value / 100;
        else DamageResistanceTable.values(resistance)[id - 1] = value;
        GuiNpcSlider slider = getSlider(id);
        if (slider != null) { slider.sliderValue = (value + DamageResistanceTable.LIMIT) / (2 * DamageResistanceTable.LIMIT); slider.setString(percent(value)); }
    }
    @Override public void mouseDragged(GuiNpcSlider slider) {
        float value = Math.round(slider.sliderValue * 2 * DamageResistanceTable.LIMIT - DamageResistanceTable.LIMIT);
        int id = slider.field_146127_k;
        if (id == 0) resistance.knockback = 1 + value / 100;
        else DamageResistanceTable.values(resistance)[id - 1] = value;
        getTextField(id).setText(Float.toString(value)); slider.setString(percent(value));
    }
    private static String percent(float value) { return (value == (int)value ? Integer.toString((int)value) : Float.toString(value)) + "%"; }
    @Override public void mouseReleased(GuiNpcSlider slider) { mouseDragged(slider); }
    @Override public void mousePressed(GuiNpcSlider slider) { mouseDragged(slider); }
    @Override public void buttonEvent(GuiButton button) { if (button.id == 66) close(); }
    @Override public void save() { commit(); }
    @Override public void mouseClicked(int x, int y, int button) throws java.io.IOException {
        if (button == 0 && x >= guiLeft + xSize - 17 && x < guiLeft + xSize - 4 && y >= guiTop + 38 && y < guiTop + 38 + rows * 24) {
            dragging = true; scrollTo(y); return;
        }
        super.func_73864_a(x, y, button);
    }
    @Override protected void mouseClickMove(int x, int y, int button, long time) { if (dragging) scrollTo(y); else super.mouseClickMove(x, y, button, time); }
    @Override public void mouseReleased(int x, int y, int button) { dragging = false; super.func_146286_b(x, y, button); }
    private void scrollTo(int y) {
        moveOffset(Math.round((y - guiTop - 38f) / (rows * 24) * (DamageResistanceTable.TYPES.length - rows)));
    }
    private void moveOffset(int next) {
        next = Math.max(0, Math.min(DamageResistanceTable.TYPES.length - rows, next));
        if (next == offset) return;
        commit(); GuiNpcTextField.unfocus(); fields.clear(); offset = next; initGui();
    }
    @Override public void drawScreen(int x, int y, float partial) {
        // Native parent screens forward drawing/clicks, but not wheel or drag/release events.
        int wheel = Mouse.getDWheel();
        if (wheel != 0 && !hasSubGui() && x >= guiLeft && x < guiLeft + xSize && y >= guiTop && y < guiTop + ySize)
            moveOffset(offset - Integer.signum(wheel));
        if (dragging) { if (Mouse.isButtonDown(0)) scrollTo(y); else dragging = false; }
        ResourceLocation texture = background; background = null;
        GlStateManager.color(1, 1, 1, 1); mc.getTextureManager().bindTexture(texture);
        Gui.drawScaledCustomSizeModalRect(guiLeft, guiTop, 0, 0, 256, 184, xSize, ySize, 256, 256);
        super.func_73863_a(x, y, partial); background = texture;
        mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(Items.IRON_CHESTPLATE), guiLeft + 6, guiTop + 10);
        for (int n = 0; n < rows; n++) {
            Item item = Item.REGISTRY.getObject(new ResourceLocation("minecraft", ICONS[offset + n]));
            mc.getRenderItem().renderItemAndEffectIntoGUI(new ItemStack(item), guiLeft + 6, guiTop + 40 + n * 24);
        }
        int top = guiTop + 38, track = rows * 24;
        drawRect(guiLeft + xSize - 15, top, guiLeft + xSize - 8, top + track, 0xFF333333);
        int thumb = Math.max(12, track * rows / DamageResistanceTable.TYPES.length);
        int sy = top + offset * (track - thumb) / (DamageResistanceTable.TYPES.length - rows);
        drawRect(guiLeft + xSize - 15, sy, guiLeft + xSize - 8, sy + thumb, 0xFFFFFFFF);
        if (x >= guiLeft + 27 && x < guiLeft + 128) {
            int index = y >= guiTop + 38 ? offset + (y - guiTop - 38) / 24 : -1;
            if ((index == -1 && y >= guiTop + 8 && y < guiTop + 28) || (index >= offset && index < offset + rows && index < DamageResistanceTable.TYPES.length))
                drawHoveringText(I18n.format(index < 0 ? "cnpcplus.resistance.knockback" : "cnpcplus.resistance." + DamageResistanceTable.TYPES[index]), x, y);
        }
    }
}
