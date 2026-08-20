package bin.cnpcplus.smelting.client;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.smelting.ContainerSmeltingRecipes;
import bin.cnpcplus.smelting.SmeltingLayout;
import bin.cnpcplus.smelting.SmeltingRecipeData;
import bin.cnpcplus.smelting.SmeltingRecipeRegistry;
import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.ITextfieldListener;

/**
 * The visual smelting recipe editor.
 *
 * Layout is 1:1 with the 1.20.1 / 1.21.1 versions (see SmeltingLayout). The whole
 * panel is shifted down by SmeltingLayout.GUI_OFFSET_Y so CNPC's top menu row is
 * not clipped by the top of the window at the default resolution.
 *
 * All writes go through the server: the client never edits the registry or the
 * container slots directly.
 */
public class GuiSmeltingRecipes extends GuiContainerNPCInterface2
        implements ICustomScrollListener, ITextfieldListener {
    private static final ResourceLocation SLOT_TEX =
            new ResourceLocation("customnpcs", "textures/gui/slot.png");
    private static final ResourceLocation FURNACE_TEX =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");

    private static final int BTN_SAVE = 2;
    private static final int BTN_NEW = 3;
    private static final int BTN_REMOVE = 4;

    private static final int BTN_GENERIC = 7;

    private static final int FIELD_NAME = 30;
    private static final int FIELD_TIME = 20;
    private static final int FIELD_XP = 21;

    private final ContainerSmeltingRecipes container;
    private GuiCustomScroll scroll;
    private List<SmeltingRecipeData> recipes = new ArrayList<SmeltingRecipeData>();

    private int selectedId;
    private String recipeName = "";
    private float cookTime = 200.0F;
    private float xp = 0.0F;
    private boolean blast;
    private boolean smoker;
    private boolean generic;

    /**
     * The npc must not be null. Every top menu button routes through
     * GuiNpcMenu.topButtonPressed -> CustomNpcs.proxy.openGui(this.npc, ...), and
     * the screens it builds dereference it immediately (GuiNpcStats does
     * this.stats = npc.stats), so a null here crashes the game the moment the
     * player switches tabs. CNPC already remembers the npc the player opened the
     * menu with, so reusing it keeps those tabs working.
     */
    public GuiSmeltingRecipes(ContainerSmeltingRecipes container, int selectedId) {
        super(noppes.npcs.client.NoppesUtil.getLastNpc(), container, 5);
        this.container = container;
        this.selectedId = selectedId;
        this.drawDefaultBackground = false;
        this.setBackground("inventorymenu.png");
        // GuiContainerNPCInterface extends GuiContainer directly, so these are the
        // vanilla fields and the only ones; its initGui centres the panel from them.
        this.xSize = SmeltingLayout.GUI_WIDTH;
        this.ySize = SmeltingLayout.GUI_HEIGHT;
        this.title = "";
        this.closeOnEsc = true;
    }

    @Override
    public void initGui() {
        super.initGui();
        // GuiContainerNPCInterface redeclares field_147003_i / field_147009_r as its
        // own public fields, shadowing GuiContainer's protected guiLeft / guiTop
        // (verified in the constant pool: the owner of both Fieldrefs is
        // GuiContainerNPCInterface). Its initGui only centres its own copies, so the
        // vanilla pair keeps whatever GuiContainer computed for the default 176x166
        // panel. Vanilla draws slots and hit-tests them from *its* pair, while CNPC
        // draws the panel and widgets from *its* pair, which is why the slots looked
        // correct but could not be clicked where they appeared.
        //
        // Writing both keeps the two coordinate systems in step. cnpcplus$syncVanillaOrigin
        // does it after every change, including the downward nudge that lifts CNPC's
        // top menu row clear of the screen edge.
        this.guiTop += SmeltingLayout.GUI_OFFSET_Y;
        this.cnpcplus$syncVanillaOrigin();
        this.repositionMenu();

        this.pullFromRegistry();

        if (this.scroll == null) {
            this.scroll = new GuiCustomScroll(this, 0);
        }
        this.scroll.setSize(SmeltingLayout.SCROLL_WIDTH, SmeltingLayout.SCROLL_HEIGHT);
        this.scroll.guiLeft = this.guiLeft + SmeltingLayout.SCROLL_X;
        this.scroll.guiTop = this.guiTop + SmeltingLayout.SCROLL_Y;
        this.scroll.setUnsortedList(this.buildListEntries());
        this.addScroll(this.scroll);

        this.addTextField(new GuiNpcTextField(FIELD_NAME, this, this.getFontRenderer(),
                this.guiLeft + SmeltingLayout.NAME_X, this.guiTop + SmeltingLayout.NAME_Y,
                SmeltingLayout.NAME_WIDTH, SmeltingLayout.NAME_HEIGHT, this.recipeName));

        int bx = this.guiLeft + SmeltingLayout.BTN_X;
        int by = this.guiTop + SmeltingLayout.BTN_Y;
        int bs = SmeltingLayout.BTN_SPACING;
        int bw = SmeltingLayout.BTN_WIDTH;
        int bh = SmeltingLayout.BTN_HEIGHT;
        this.addButton(new GuiNpcButton(BTN_NEW, bx, by, bw, bh, "gui.add"));
        this.addButton(new GuiNpcButton(BTN_REMOVE, bx, by + bs, bw, bh, "gui.remove"));
        this.addButton(new GuiNpcButton(BTN_SAVE, bx, by + bs * 2, bw, bh, "gui.save"));

        // Only the generic fuel toggle exists in 1.12.2: this version has a single
        // furnace, so blast and smoker have nothing to apply to. It takes the middle
        // slot of the old three-button column.
        this.addButton(new GuiIconToggleButton(BTN_GENERIC,
                this.guiLeft + SmeltingLayout.TOGGLE_X,
                this.guiTop + SmeltingLayout.TOGGLE_Y + SmeltingLayout.TOGGLE_SPACING,
                SmeltingLayout.TOGGLE_WIDTH, SmeltingLayout.TOGGLE_HEIGHT,
                new ItemStack(Items.COAL), this.generic, "cnpcplus.smelting.generic"));

        // Always present, including while defining a brand new recipe: they were
        // previously gated on a recipe being selected, which meant the cook time and
        // xp of a new recipe could never be entered.
        int fw = SmeltingLayout.FIELD_WIDTH;
        int fh = SmeltingLayout.FIELD_HEIGHT;
        // Not numbersOnly: that mode rejects '.' so decimals could not be typed.
        this.addTextField(new GuiNpcTextField(FIELD_TIME, this, this.getFontRenderer(),
                this.guiLeft + SmeltingLayout.TIME_X, this.guiTop + SmeltingLayout.TIME_Y,
                fw, fh, trimFloat(this.cookTime)));
        this.addTextField(new GuiNpcTextField(FIELD_XP, this, this.getFontRenderer(),
                this.guiLeft + SmeltingLayout.XP_X, this.guiTop + SmeltingLayout.XP_Y,
                fw, fh, trimFloat(this.xp)));
    }

    /**
     * CNPC's top menu row is positioned from guiTop by super.initGui, so once the
     * panel moves the row has to be re-initialised or it stays where it was.
     * GuiContainerNPCInterface2 keeps the menu in a private field, so it is reached
     * by reflection; failing to move it only leaves the row in the old spot, so a
     * failure here is logged rather than propagated.
     */
    private void repositionMenu() {
        try {
            java.lang.reflect.Field field =
                    GuiContainerNPCInterface2.class.getDeclaredField("menu");
            field.setAccessible(true);
            Object menu = field.get(this);
            if (menu == null) {
                return;
            }
            menu.getClass()
                    .getMethod("initGui", int.class, int.class, int.class)
                    .invoke(menu, Integer.valueOf(this.guiLeft),
                            Integer.valueOf(this.guiTop + this.menuYOffset),
                            Integer.valueOf(this.xSize));
        } catch (Exception e) {
            bin.cnpcplus.CnpcPlus.LOGGER.error(
                    "[Smelting] could not reposition the CNPC menu row", e);
        }
    }

    /**
     * Copies the CNPC origin onto GuiContainer's own shadowed guiLeft / guiTop.
     *
     * Both fields are named identically, so plain field access here resolves to
     * CNPC's public pair and cannot reach vanilla's protected pair at all; only
     * reflection against GuiContainer itself can. Without this the two coordinate
     * systems disagree by however much CNPC's centring differs from vanilla's,
     * which is exactly the slot offset seen in game.
     */
    private void cnpcplus$syncVanillaOrigin() {
        // Obfuscated name first: reflection resolves names at runtime, where the
        // field really is field_147003_i. The MCP name is the dev-environment
        // fallback, since reobf does not rewrite strings.
        setVanillaField("field_147003_i", "guiLeft", this.guiLeft);
        setVanillaField("field_147009_r", "guiTop", this.guiTop);
    }

    private void setVanillaField(String srgName, String mcpName, int value) {
        Class<?> owner = net.minecraft.client.gui.inventory.GuiContainer.class;
        for (String name : new String[] {srgName, mcpName}) {
            try {
                java.lang.reflect.Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                f.setInt(this, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Try the other naming scheme.
            } catch (Exception e) {
                bin.cnpcplus.CnpcPlus.LOGGER.error(
                        "[Smelting] could not write vanilla field " + name, e);
                return;
            }
        }
        bin.cnpcplus.CnpcPlus.LOGGER.error(
                "[Smelting] vanilla slot origin field not found: {} / {}", srgName, mcpName);
    }

    private void pullFromRegistry() {
        this.recipes = SmeltingRecipeRegistry.list();
        SmeltingRecipeData selected = null;
        for (int i = 0; i < this.recipes.size(); ++i) {
            SmeltingRecipeData data = this.recipes.get(i);
            if (data != null && data.id == this.selectedId) {
                selected = data;
                break;
            }
        }
        if (selected == null) {
            if (this.selectedId >= 0) {
                this.selectedId = -1;
            }
            return;
        }
        this.recipeName = selected.name;
        this.cookTime = selected.cookTime;
        this.xp = selected.xp;
        this.blast = selected.blastAllowed;
        this.smoker = selected.smokerAllowed;
        this.generic = selected.genericFuelAllowed;
    }

    /** Same-named recipes get their id appended so the list stays unambiguous. */
    private List<String> buildListEntries() {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < this.recipes.size(); ++i) {
            SmeltingRecipeData data = this.recipes.get(i);
            if (data == null) {
                continue;
            }
            String label = data.name == null || data.name.isEmpty() ? "?" : data.name;
            int duplicates = 0;
            for (int j = 0; j < this.recipes.size(); ++j) {
                SmeltingRecipeData other = this.recipes.get(j);
                if (other != null && label.equals(other.name)) {
                    ++duplicates;
                }
            }
            out.add(duplicates > 1 ? label + " (" + data.id + ")" : label);
        }
        return out;
    }

    /** Called from the sync packet so the view always reflects the server. */
    public void refreshFromServer(int serverSelectedId) {
        if (serverSelectedId != this.selectedId) {
            this.selectedId = serverSelectedId;
        }
        this.initGui();
    }

    // actionPerformed is declared throws IOException in 1.12.2.
    @Override
    protected void actionPerformed(GuiButton guibutton) throws java.io.IOException {
        if (guibutton instanceof GuiIconToggleButton) {
            GuiIconToggleButton toggle = (GuiIconToggleButton) guibutton;
            // The base class flips the value on click; read it back afterwards.
            super.actionPerformed(guibutton);
            if (toggle.id == BTN_GENERIC) {
                this.generic = toggle.isOn();
            }
            return;
        }
        super.actionPerformed(guibutton);
    }

    @Override
    public void buttonEvent(GuiButton guibutton) {
        int id = guibutton.id;
        if (id == BTN_NEW) {
            this.selectedId = -1;
            this.recipeName = "new" + System.currentTimeMillis() % 100000L;
            this.cookTime = 200.0F;
            this.xp = 0.0F;
            this.blast = false;
            this.smoker = false;
            this.generic = false;
            // The server clears its own slots and echoes the result back.
            this.send(PacketSmeltingAction.ACTION_NEW, -1);
            this.initGui();
        } else if (id == BTN_REMOVE) {
            if (this.selectedId >= 0) {
                this.send(PacketSmeltingAction.ACTION_REMOVE, this.selectedId);
                this.selectedId = -1;
            }
        } else if (id == BTN_SAVE) {
            this.readFields();
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketSmeltingAction(
                    PacketSmeltingAction.ACTION_SAVE, this.selectedId, this.recipeName,
                    this.cookTime, this.xp, this.blast, this.smoker, this.generic));
        }
    }

    private void readFields() {
        GuiNpcTextField name = this.getTextField(FIELD_NAME);
        if (name != null) {
            this.recipeName = name.getText();
        }
        GuiNpcTextField time = this.getTextField(FIELD_TIME);
        if (time != null) {
            this.cookTime = parseFloat(time.getText(), 200.0F);
        }
        GuiNpcTextField xpField = this.getTextField(FIELD_XP);
        if (xpField != null) {
            this.xp = parseFloat(xpField.getText(), 0.0F);
        }
    }

    @Override
    public void unFocused(GuiNpcTextField textfield) {
        this.readFields();
    }

    @Override
    public void scrollClicked(int x, int y, int button, GuiCustomScroll source) {
        if (source == null || source.selected < 0 || source.selected >= this.recipes.size()) {
            return;
        }
        // Selected by index, not name: two recipes may share a name.
        SmeltingRecipeData data = this.recipes.get(source.selected);
        if (data == null) {
            return;
        }
        this.selectedId = data.id;
        this.send(PacketSmeltingAction.ACTION_SELECT, data.id);
        this.initGui();
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScroll source) {
        this.scrollClicked(0, 0, 0, source);
    }

    private void send(int action, int id) {
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketSmeltingAction(action, id));
    }

    /**
     * Declared under the MCP name only, and reobf renames it to func_146976_a.
     *
     * Declaring both names looks harmless in source, but after reobf they become
     * two identical func_146976_a(float,int,int) methods, which is a
     * ClassFormatError ("Duplicate method name&signature") that makes this whole
     * class unloadable - the GUI then silently fails to open.
     *
     * The super call has to use the SRG name: CNPC ships obfuscated, so its
     * GuiContainerNPCInterface override is only visible to the compiler as
     * func_146976_a, and calling the MCP name here would not resolve.
     */
    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // Draws the CNPC panel background and the top menu row.
        super.func_146976_a(partialTicks, mouseX, mouseY);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Slot frames are drawn from the container's real slot coordinates so they
        // can never drift from the slots themselves.
        this.drawSlotBackground(ContainerSmeltingRecipes.SLOT_INDEX_INPUT);
        this.drawSlotBackground(ContainerSmeltingRecipes.SLOT_INDEX_FUEL);
        this.drawSlotBackground(ContainerSmeltingRecipes.SLOT_INDEX_OUTPUT);

        this.mc.getTextureManager().bindTexture(FURNACE_TEX);
        int flameX = this.guiLeft + SmeltingLayout.FLAME_X;
        int flameY = this.guiTop + SmeltingLayout.FLAME_Y;
        int flame = (int) (System.currentTimeMillis() / 100L % 14L);
        this.drawTexturedModalRect(flameX, flameY + 12 - flame, 176, 12 - flame, 14, flame + 1);

        int arrowX = this.guiLeft + SmeltingLayout.ARROW_X;
        int arrowY = this.guiTop + SmeltingLayout.ARROW_Y;
        int cookTicks = Math.max(20, Math.round(this.cookTime));
        int progress = (int) (System.currentTimeMillis() / 50L % cookTicks) * 24 / cookTicks;
        this.drawTexturedModalRect(arrowX, arrowY, 176, 14, progress + 1, 16);
    }

    private void drawSlotBackground(int slotIndex) {
        this.mc.getTextureManager().bindTexture(SLOT_TEX);
        int sx = this.container.slotRenderX(slotIndex);
        int sy = this.container.slotRenderY(slotIndex);
        // -1 so the 18x18 frame surrounds the 16x16 slot.
        this.drawTexturedModalRect(this.guiLeft + sx - 1, this.guiTop + sy - 1, 0, 0, 18, 18);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        if (this.hasSubGui()) {
            return;
        }
        // The numeric fields and toggles carry no visible label, so they explain
        // themselves on hover.
        this.drawFieldTooltip(FIELD_TIME, "cnpcplus.smelting.cooktime", mouseX, mouseY);
        this.drawFieldTooltip(FIELD_XP, "cnpcplus.smelting.xp", mouseX, mouseY);
        for (Object obj : this.buttonList) {
            if (!(obj instanceof GuiIconToggleButton)) {
                continue;
            }
            GuiIconToggleButton toggle = (GuiIconToggleButton) obj;
            if (mouseX >= toggle.x && mouseX < toggle.x + toggle.width
                    && mouseY >= toggle.y && mouseY < toggle.y + toggle.height) {
                this.drawHoveringText(
                        net.minecraft.client.resources.I18n.format(toggle.getTooltipKey()),
                        mouseX - this.guiLeft, mouseY - this.guiTop);
                break;
            }
        }
    }

    private void drawFieldTooltip(int fieldId, String key, int mouseX, int mouseY) {
        GuiNpcTextField field = this.getTextField(fieldId);
        if (field == null) {
            return;
        }
        if (mouseX >= field.x && mouseX < field.x + field.width
                && mouseY >= field.y && mouseY < field.y + field.height) {
            this.drawHoveringText(net.minecraft.client.resources.I18n.format(key),
                    mouseX - this.guiLeft, mouseY - this.guiTop);
        }
    }

    @Override
    public void save() {
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String trimFloat(float value) {
        if (value == Math.round(value)) {
            return Integer.toString(Math.round(value));
        }
        return Float.toString(value);
    }
}
