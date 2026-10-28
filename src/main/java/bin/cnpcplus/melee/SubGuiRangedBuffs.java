package bin.cnpcplus.melee;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.entity.data.DataRanged;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 238-4：远程投掷物的多效果编辑界面。 */
public class SubGuiRangedBuffs extends SubGuiInterface
        implements ITextfieldListener, ICustomScrollListener {

    private static final int SCROLL_AVAILABLE = 0;
    private static final int SCROLL_SELECTED = 1;
    private static final int FIELD_AMP = 2;
    private static final int FIELD_SECONDS = 3;
    private static final int BTN_ADD = 11;
    private static final int BTN_REMOVE = 12;
    private static final int BTN_CLEAR = 14;
    private static final int BTN_DONE = 66;

    private final DataRanged ranged;
    private GuiCustomScroll available;
    private GuiCustomScroll selected;
    private final Map<String, String> availableNames = new HashMap<String, String>();
    private final Map<String, String> selectedNames = new HashMap<String, String>();
    private int amplifier;
    private int seconds = 5;

    public SubGuiRangedBuffs(DataRanged ranged) {
        this.ranged = ranged;
        this.setBackground("menubg.png");
        this.xSize = 420;
        this.ySize = 240;
        this.closeOnEsc = true;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addLabel(new GuiNpcLabel(0, "cnpcplus.ranged.buffs", this.guiLeft + 4, this.guiTop + 6));
        this.addLabel(new GuiNpcLabel(1, "stats.amplify", this.guiLeft + 150, this.guiTop + 11));
        this.addTextField(new GuiNpcTextField(FIELD_AMP, this, this.fontRenderer,
                this.guiLeft + 210, this.guiTop + 6, 40, 18, this.amplifier + ""));
        this.getTextField(FIELD_AMP).numbersOnly = true;
        this.getTextField(FIELD_AMP).setMinMaxDefault(0, RangedBuffStore.MAX_AMPLIFIER, 0);

        this.addLabel(new GuiNpcLabel(2, "gui.time", this.guiLeft + 262, this.guiTop + 11));
        this.addTextField(new GuiNpcTextField(FIELD_SECONDS, this, this.fontRenderer,
                this.guiLeft + 310, this.guiTop + 6, 50, 18, this.seconds + ""));
        this.getTextField(FIELD_SECONDS).numbersOnly = true;
        this.getTextField(FIELD_SECONDS).setMinMaxDefault(1, RangedBuffStore.MAX_SECONDS, 5);

        if (this.available == null) {
            this.available = new GuiCustomScroll(this, SCROLL_AVAILABLE);
            this.available.setSize(150, 170);
        }
        this.available.guiLeft = this.guiLeft + 4;
        this.available.guiTop = this.guiTop + 40;
        this.addScroll(this.available);
        this.addLabel(new GuiNpcLabel(11, "beacon.availableEffects", this.guiLeft + 4, this.guiTop + 30));

        if (this.selected == null) {
            this.selected = new GuiCustomScroll(this, SCROLL_SELECTED);
            this.selected.setSize(190, 170);
        }
        this.selected.guiLeft = this.guiLeft + 226;
        this.selected.guiTop = this.guiTop + 40;
        this.addScroll(this.selected);
        this.addLabel(new GuiNpcLabel(12, "beacon.currentEffects", this.guiLeft + 226, this.guiTop + 30));

        this.cnpcplus$rebuildLists();
        this.addButton(new GuiNpcButton(BTN_ADD, this.guiLeft + 158, this.guiTop + 60, 60, 20, ">"));
        this.addButton(new GuiNpcButton(BTN_REMOVE, this.guiLeft + 158, this.guiTop + 84, 60, 20, "<"));
        this.addButton(new GuiNpcButton(BTN_CLEAR, this.guiLeft + 158, this.guiTop + 112, 60, 20, "<<"));
        this.addButton(new GuiNpcButton(BTN_DONE, this.guiLeft + 330, this.guiTop + 216, 84, 20, "gui.done"));
    }

    private void cnpcplus$rebuildLists() {
        this.availableNames.clear();
        this.selectedNames.clear();
        Map<String, int[]> chosen = RangedBuffStore.get(this.ranged);

        List<String> availableList = new ArrayList<String>();
        if (!chosen.containsKey(RangedBuffStore.FIRE_KEY)) {
            String fire = I18n.format("cnpcplus.ranged.fire");
            this.availableNames.put(fire, RangedBuffStore.FIRE_KEY);
            availableList.add(fire);
        }
        for (Potion potion : Potion.REGISTRY) {
            String registry = RangedBuffStore.nameOf(potion);
            if (registry == null || chosen.containsKey(registry)) continue;
            String name = potion.getName();
            String display = I18n.format(name);
            if (display.equals(name)) display = registry;
            if (this.availableNames.containsKey(display)) display += " [" + registry + "]";
            this.availableNames.put(display, registry);
            availableList.add(display);
        }
        this.available.setUnsortedList(availableList);

        List<String> selectedList = new ArrayList<String>();
        for (Map.Entry<String, int[]> entry : chosen.entrySet()) {
            String display;
            if (RangedBuffStore.isFire(entry.getKey())) {
                display = I18n.format("cnpcplus.ranged.fire") + " (" + entry.getValue()[1] + "s)";
            } else {
                Potion potion = RangedBuffStore.resolve(entry.getKey());
                String base;
                if (potion == null) {
                    base = entry.getKey();
                } else {
                    String name = I18n.format(potion.getName());
                    base = name.equals(potion.getName()) ? entry.getKey() : name;
                }
                display = base + " "
                        + I18n.format("enchantment.level." + (entry.getValue()[0] + 1))
                        + " (" + entry.getValue()[1] + "s)";
            }
            if (this.selectedNames.containsKey(display)) display += " [" + entry.getKey() + "]";
            this.selectedNames.put(display, entry.getKey());
            selectedList.add(display);
        }
        this.selected.setUnsortedList(selectedList);
    }

    private void cnpcplus$readFields() {
        GuiNpcTextField amp = this.getTextField(FIELD_AMP);
        GuiNpcTextField time = this.getTextField(FIELD_SECONDS);
        if (amp != null) this.amplifier = RangedBuffStore.clampAmplifier(amp.getInteger());
        if (time != null) this.seconds = RangedBuffStore.clampSeconds(time.getInteger());
    }

    @Override
    public void unFocused(GuiNpcTextField textfield) {
        if (this.getTextField(FIELD_AMP) == textfield) {
            this.amplifier = RangedBuffStore.clampAmplifier(textfield.getInteger());
        } else if (this.getTextField(FIELD_SECONDS) == textfield) {
            this.seconds = RangedBuffStore.clampSeconds(textfield.getInteger());
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        this.cnpcplus$readFields();
        int id = button.id;
        if (id == BTN_ADD) {
            if (this.available.hasSelected()) {
                String registry = this.availableNames.get(this.available.getSelected());
                if (registry != null) {
                    RangedBuffStore.put(this.ranged, registry, this.amplifier, this.seconds);
                }
            }
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
        } else if (id == BTN_REMOVE) {
            if (this.selected.hasSelected()) {
                String registry = this.selectedNames.get(this.selected.getSelected());
                if (registry != null) RangedBuffStore.remove(this.ranged, registry);
            }
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
        } else if (id == BTN_CLEAR) {
            RangedBuffStore.clear(this.ranged);
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
        } else if (id == BTN_DONE) {
            this.close();
        }
    }

    @Override
    public void scrollClicked(int mouseX, int mouseY, int button, GuiCustomScroll scroll) {
        if (scroll != this.selected || !this.selected.hasSelected()) return;
        String registry = this.selectedNames.get(this.selected.getSelected());
        if (registry == null) return;
        int[] value = RangedBuffStore.get(this.ranged).get(registry);
        if (value == null) return;
        this.amplifier = value[0];
        this.seconds = value[1];
        GuiNpcTextField amp = this.getTextField(FIELD_AMP);
        if (amp != null) amp.setText(String.valueOf(this.amplifier));
        GuiNpcTextField time = this.getTextField(FIELD_SECONDS);
        if (time != null) time.setText(String.valueOf(this.seconds));
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScroll scroll) {
        if (scroll == this.available) {
            String registry = this.availableNames.get(selection);
            if (registry == null) return;
            this.cnpcplus$readFields();
            RangedBuffStore.put(this.ranged, registry, this.amplifier, this.seconds);
            this.available.selected = -1;
            this.selected.selected = -1;
            this.initGui();
        } else if (scroll == this.selected) {
            String registry = this.selectedNames.get(selection);
            if (registry == null) return;
            this.cnpcplus$readFields();
            RangedBuffStore.put(this.ranged, registry, this.amplifier, this.seconds);
            this.initGui();
        }
    }
}
