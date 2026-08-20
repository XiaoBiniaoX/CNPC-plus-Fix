package bin.cnpcplus.smelting;

/**
 * All smelting GUI coordinates, kept 1:1 with the 1.20.1 / 1.21.1 modules so the
 * three versions look identical. These values were settled by testing over
 * several rounds in 1.20.1; do not "tidy" them.
 */
public final class SmeltingLayout {
    private SmeltingLayout() {}

    /**
     * Whole-panel downward shift, applied in the GUI to guiTop.
     *
     * CNPC draws its top menu row at guiTop - 17, so without a shift that row is
     * clipped by the top of the screen. 1.20.1 uses 52, but its panel is the
     * stock 200 tall while this one is 250: centring already places this panel 25
     * higher-reaching, so reusing 52 pushed it visibly too low. 24 clears the menu
     * row with a small margin and keeps the panel vertically balanced.
     */
    public static final int GUI_OFFSET_Y = 24;

    // Recipe slots. Container slot order is INPUT, FUEL, OUTPUT.
    public static final int SLOT_INPUT_X = 56;
    public static final int SLOT_INPUT_Y = 35;
    public static final int SLOT_FUEL_X = 56;
    public static final int SLOT_FUEL_Y = 70;
    public static final int SLOT_OUTPUT_X = 110;
    public static final int SLOT_OUTPUT_Y = 55;

    // Vanilla furnace flame / progress arrow, drawn purely as decoration.
    public static final int FLAME_X = 57;
    public static final int FLAME_Y = 55;
    public static final int ARROW_X = 79;
    public static final int ARROW_Y = 55;

    // Three icon toggles, stacked vertically.
    public static final int TOGGLE_X = 14;
    public static final int TOGGLE_Y = 40;
    public static final int TOGGLE_SPACING = 22;
    public static final int TOGGLE_WIDTH = 20;
    public static final int TOGGLE_HEIGHT = 20;

    // Recipe name field (top left).
    public static final int NAME_X = 8;
    public static final int NAME_Y = 8;
    public static final int NAME_WIDTH = 160;
    public static final int NAME_HEIGHT = 20;

    /**
     * Cook time field. The only free vertical band is y28..36: the name field
     * occupies y8..28 and the output slot background y54..72, and this field
     * (x100..133) overlaps the output slot column. 32 leaves 4px either side.
     */
    public static final int TIME_X = 100;
    public static final int TIME_Y = 32;
    public static final int XP_X = 100;
    public static final int XP_Y = 80;
    public static final int FIELD_WIDTH = 33;
    public static final int FIELD_HEIGHT = 18;

    // Right hand button column (add / remove / save).
    public static final int BTN_X = 306;
    public static final int BTN_Y = 12;
    public static final int BTN_WIDTH = 84;
    public static final int BTN_HEIGHT = 20;
    public static final int BTN_SPACING = 22;

    // Recipe list on the right of the panel.
    public static final int SCROLL_X = 172;
    public static final int SCROLL_Y = 8;
    public static final int SCROLL_WIDTH = 130;
    public static final int SCROLL_HEIGHT = 180;

    // Panel size.
    public static final int GUI_WIDTH = 420;
    public static final int GUI_HEIGHT = 250;

    // Player inventory, standard vanilla placement.
    public static final int INV_X = 8;
    public static final int INV_Y = 113;
    public static final int HOTBAR_Y = 171;
}
