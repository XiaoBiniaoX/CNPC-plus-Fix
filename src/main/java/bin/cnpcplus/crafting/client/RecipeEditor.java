package bin.cnpcplus.crafting.client;

import bin.cnpcplus.crafting.CraftKind;
import bin.cnpcplus.crafting.CustomRecipe;
import bin.cnpcplus.crafting.RecipeContainer;
import bin.cnpcplus.crafting.network.PacketRecipeAction;
import bin.cnpcplus.crafting.network.PacketRecipeState;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.fx.client.FxEditor;
import bin.cnpcplus.smelting.client.GuiIconToggleButton;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import noppes.npcs.client.gui.util.ISubGuiListener;
import noppes.npcs.client.gui.util.ITextfieldListener;
import noppes.npcs.client.gui.util.SubGuiInterface;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** 三种配方沿用 1.20.1 的槽位、列表与按钮布局，服务端槽位是物品唯一来源。 */
public final class RecipeEditor extends GuiContainerNPCInterface2 implements ICustomScrollListener, ITextfieldListener, ISubGuiListener {
    private static final ResourceLocation SLOT = new ResourceLocation("customnpcs", "textures/gui/slot.png");
    private final RecipeContainer container;
    private final List<Integer> ids = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private GuiCustomScroll scroll;
    private CustomRecipe edit;
    private PacketRecipeState applied;
    /** 服务端保存失败原因（lang key），4 秒后自动消失。 */
    private String notice = "";
    private long noticeUntil;

    public RecipeEditor(RecipeContainer container) {
        super(noppes.npcs.client.NoppesUtil.getLastNpc(), container, 5);
        this.container = container;
        this.edit = new CustomRecipe(container.kind);
        this.xSize = 420;
        this.ySize = 200;
        this.setBackground("inventorymenu.png");
        this.drawDefaultBackground = false;
        this.closeOnEsc = true;
        this.title = "";
    }

    public void accept(PacketRecipeState state) {
        if (state.window != container.windowId || state.kind != container.kind.ordinal()) return;
        applied = state;
        if (state.notice != null && !state.notice.isEmpty()) {
            notice = state.notice;
            noticeUntil = System.currentTimeMillis() + 4000L;
        }
        edit = CustomRecipe.fromNBT(container.kind, state.data);
        container.recipe = edit.copy();
        ids.clear(); names.clear();
        NBTTagList entries = state.data.getTagList("Entries", 10);
        for (int i = 0; i < entries.tagCount(); i++) {
            NBTTagCompound entry = entries.getCompoundTagAt(i);
            ids.add(entry.getInteger("Id"));
            names.add(entry.getInteger("Id") + ": " + entry.getString("Name"));
        }
        this.initGui();
    }

    @Override public void updateScreen() {
        super.updateScreen();
        PacketRecipeState state = RecipeClientState.current;
        if (state != null && state != applied && state.window == container.windowId && state.kind == container.kind.ordinal()) accept(state);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiTop = Math.max(20, Math.min(this.height - this.ySize - 4, (this.height - this.ySize) / 2));
        alignOrigins();
        if (scroll == null) scroll = new GuiCustomScroll(this, 0);
        scroll.setSize(130, 180);
        scroll.guiLeft = this.guiLeft + 172; scroll.guiTop = this.guiTop + 8;
        scroll.setUnsortedList(names);
        scroll.selected = ids.indexOf(edit.id);
        this.addScroll(scroll);
        boolean anvil = container.kind == CraftKind.ANVIL;
        this.addTextField(new GuiNpcTextField(30, this, this.getFontRenderer(),
                this.guiLeft + (anvil ? 42 : 8), this.guiTop + 8, anvil ? 126 : 158, 20, edit.name));
        this.getTextField(30).setMaxStringLength(256);
        this.addButton(new GuiNpcButton(1, this.guiLeft + 306, this.guiTop + 12, 84, 20, "gui.add"));
        this.addButton(new GuiNpcButton(2, this.guiLeft + 306, this.guiTop + 34, 84, 20, "gui.remove"));
        this.addButton(new GuiNpcButton(3, this.guiLeft + 306, this.guiTop + 56, 84, 20, "gui.save"));
        int nx = container.kind == CraftKind.DROP ? 112 : container.kind == CraftKind.SMITHING ? 118 : 142;
        int ny = container.kind == CraftKind.DROP ? 59 : container.kind == CraftKind.SMITHING ? 47 : 53;
        this.addButton(new GuiIconToggleButton(4, this.guiLeft + nx, this.guiTop + ny, 18, 18,
                new ItemStack(Items.NAME_TAG), edit.nameOnly, "cnpcplus.smelting.nameonly"));
        if (anvil) {
            this.addTextField(new GuiNpcTextField(31, this, this.getFontRenderer(), this.guiLeft + 134, this.guiTop + 75, 36, 20, Integer.toString(edit.experience)));
            this.getTextField(31).numbersOnly = true;
            this.getTextField(31).setMinMaxDefault(-32767, 32767, 1);
        }
        if (container.kind == CraftKind.DROP) {
            this.addButton(new GuiIconToggleButton(5, this.guiLeft + 112, this.guiTop + 39, 18, 18,
                    new ItemStack(Items.RECORD_13), !edit.sound.isEmpty(), "cnpcplus.drop.sound"));
            this.addButton(new GuiIconToggleButton(6, this.guiLeft + 112, this.guiTop + 79, 18, 18,
                    new ItemStack(Items.BLAZE_POWDER), !edit.effects.isEmpty(), "cnpcplus.fx.edit"));
            this.addButton(new GuiNpcButton(7, this.guiLeft + 306, this.guiTop + 78, 84, 20, "cnpcplus.drop.bind"));
            this.addButton(new GuiNpcButton(8, this.guiLeft + 306, this.guiTop + 100, 84, 20, "cnpcplus.drop.unbind"));
            this.addButton(new GuiNpcButton(9, this.guiLeft + 306, this.guiTop + 122, 84, 20, "cnpcplus.drop.clearsound"));
        }
    }

    private void alignOrigins() {
        Class<?> owner = net.minecraft.client.gui.inventory.GuiContainer.class;
        String[][] fields = {{"field_147003_i", "guiLeft"}, {"field_147009_r", "guiTop"}};
        for (int i = 0; i < 2; i++) for (String name : fields[i]) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); field.setInt(this, i == 0 ? this.guiLeft : this.guiTop); break; }
            catch (NoSuchFieldException ignored) { }
            catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        }
        try {
            Field field = GuiContainerNPCInterface2.class.getDeclaredField("menu"); field.setAccessible(true);
            Object menu = field.get(this);
            menu.getClass().getMethod("initGui", int.class, int.class, int.class)
                    .invoke(menu, this.guiLeft, this.guiTop + this.menuYOffset, this.xSize);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("CNPC menu layout", error); }
    }

    private void readFields() {
        GuiNpcTextField name = this.getTextField(30);
        if (name != null) edit.name = name.getText();
        GuiNpcTextField cost = this.getTextField(31);
        if (cost != null) {
            try { edit.experience = Math.max(-32767, Math.min(32767, Integer.parseInt(cost.getText().trim()))); }
            catch (NumberFormatException ignored) { edit.experience = 1; }
        }
    }

    @Override public void unFocused(GuiNpcTextField field) { readFields(); }
    @Override public void save() { }

    @Override
    public void buttonEvent(GuiButton button) {
        readFields();
        switch (button.id) {
            case 1: send(PacketRecipeAction.NEW, edit.toNBT(false)); break;
            case 2: send(PacketRecipeAction.REMOVE, edit.toNBT(false)); break;
            case 3: send(PacketRecipeAction.SAVE, edit.toNBT(false)); break;
            case 4: edit.nameOnly = ((GuiIconToggleButton) button).isOn(); break;
            case 5: this.setSubGui(new GuiSoundSelection(edit.sound)); break;
            case 6: this.setSubGui(new FxEditor(list -> edit.effects = list, edit.effects)); break;
            case 7:
                edit.dimension = Integer.toString(this.mc.player.dimension);
                net.minecraft.util.math.BlockPos pos = this.mc.player.getPosition();
                edit.bindPosition = true; edit.x = pos.getX(); edit.y = pos.getY(); edit.z = pos.getZ();
                break;
            case 8: edit.dimension = ""; edit.bindPosition = false; break;
            case 9: edit.sound = ""; this.initGui(); break;
            default: break;
        }
    }

    @Override
    public void subGuiClosed(SubGuiInterface subgui) {
        if (subgui instanceof GuiSoundSelection) {
            ResourceLocation sound = ((GuiSoundSelection) subgui).selectedResource;
            if (sound != null) edit.sound = sound.toString();
        }
        this.initGui();
    }

    private void send(int action, NBTTagCompound data) {
        bin.cnpcplus.crafting.network.PacketRecipeUpload.send(new PacketRecipeAction(action, container.kind, container.windowId, data));
    }

    @Override public void scrollClicked(int x, int y, int button, GuiCustomScroll source) {
        if (source.selected < 0 || source.selected >= ids.size()) return;
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Id", ids.get(source.selected));
        send(PacketRecipeAction.SELECT, tag);
    }
    @Override public void scrollDoubleClicked(String name, GuiCustomScroll source) { scrollClicked(0, 0, 0, source); }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        super.func_146976_a(partialTicks, mouseX, mouseY);
        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(SLOT);
        for (int i = 0; i < container.kind.slots.length; i++) {
            net.minecraft.inventory.Slot slot = container.inventorySlots.get(i);
            this.drawTexturedModalRect(this.guiLeft + slot.xPos - 1, this.guiTop + slot.yPos - 1, 0, 0, 18, 18);
        }
        if (container.kind == CraftKind.ANVIL) {
            picture("anvil_hammer.png", 8, 6, 30, 30);
            picture("anvil_plus.png", 34, 57, 13, 13);
            picture("anvil_arrow.png", 83, 56, 22, 15);
        } else if (container.kind == CraftKind.SMITHING) {
            picture("anvil_arrow.png", 64, 50, 22, 15);
        } else picture("anvil_arrow.png", 54, 60, 22, 15);
    }

    private void picture(String path, int x, int y, int w, int h) {
        this.mc.getTextureManager().bindTexture(new ResourceLocation("cnpcplus", "textures/gui/" + path));
        drawModalRectWithCustomSizedTexture(this.guiLeft + x, this.guiTop + y, 0, 0, w, h, w, h);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (this.hasSubGui()) return;
        for (GuiButton button : this.buttonList) if (button instanceof GuiIconToggleButton
                && mouseX >= button.x && mouseX < button.x + button.width && mouseY >= button.y && mouseY < button.y + button.height) {
            this.drawHoveringText(net.minecraft.client.resources.I18n.format(((GuiIconToggleButton) button).getTooltipKey()), mouseX, mouseY);
        }
        if (container.kind == CraftKind.ANVIL && mouseX >= this.guiLeft + 134 && mouseX < this.guiLeft + 170
                && mouseY >= this.guiTop + 75 && mouseY < this.guiTop + 95) {
            this.drawHoveringText(net.minecraft.client.resources.I18n.format("cnpcplus.anvil.experience"), mouseX, mouseY);
        }
        if (container.kind == CraftKind.DROP && mouseX >= this.guiLeft + 306 && mouseX < this.guiLeft + 390
                && mouseY >= this.guiTop + 78 && mouseY < this.guiTop + 98) {
            this.drawHoveringText(edit.dimension.isEmpty() ? "*" : edit.dimension + " / " + edit.x + ", " + edit.y + ", " + edit.z, mouseX, mouseY);
        }
        if (!notice.isEmpty()) {
            if (System.currentTimeMillis() > noticeUntil) { notice = ""; return; }
            String text = net.minecraft.client.resources.I18n.format(notice);
            int x = (this.width - this.fontRenderer.getStringWidth(text)) / 2;
            int y = this.guiTop + this.ySize + 6;
            drawRect(x - 4, y - 3, x + this.fontRenderer.getStringWidth(text) + 4, y + 11, 0xC0202020);
            this.fontRenderer.drawString(text, x, y, 0xFF5555);
        }
    }
}
