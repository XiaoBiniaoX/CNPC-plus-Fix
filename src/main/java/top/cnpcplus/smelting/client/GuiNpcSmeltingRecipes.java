package top.cnpcplus.smelting.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import top.cnpcplus.smelting.ContainerSmeltingRecipes;
import top.cnpcplus.smelting.SmeltingLayout;
import top.cnpcplus.smelting.SmeltingRecipeData;
import top.cnpcplus.smelting.network.PacketSmeltingRemove;
import top.cnpcplus.smelting.network.PacketSmeltingRequestList;
import top.cnpcplus.smelting.network.PacketSmeltingSave;
import top.cnpcplus.smelting.network.PacketSmeltingSelect;
import top.cnpcplus.smelting.network.SmeltingPacketHandler;

import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * 可视化自定义熔炼配方编辑界面。
 * 左侧熔炉区：三个槽位 + 火焰 + 进度箭头（原版熔炉贴图，火焰跳动、箭头按配方 cookTime 循环）。
 * 高炉/烟熏/通用燃料三个开关做成图标按钮（无文字，物品图标提示用途，红框=禁用/绿框=启用），
 * 与熔炼时间/经验两个输入框一样，默认不显示，选中配方（已存在或新建）后才显示。
 * 元素坐标见 SmeltingLayout：满意的布局已硬编码，只有界面整体偏移仍走 config 热加载。
 */
public class GuiNpcSmeltingRecipes extends GuiContainerNPCInterface2<ContainerSmeltingRecipes>
        implements ICustomScrollListener {

    private static final ResourceLocation SLOT_TEX =
            new ResourceLocation("customnpcs", "textures/gui/slot.png");
    private static final ResourceLocation FURNACE_TEX =
            new ResourceLocation("minecraft", "textures/gui/container/furnace.png");

    private GuiCustomScrollNop scroll;
    private final ContainerSmeltingRecipes container;
    private final List<SmeltingRecipeData> recipes = new ArrayList<>();
    private String selectedName = null;
    private int selectedId = -1;

    private static final int BTN_NEW = 3;
    private static final int BTN_REMOVE = 4;
    private static final int BTN_SAVE = 2;
    private static final int BTN_BLAST = 5;
    private static final int BTN_SMOKER = 6;
    private static final int BTN_GENERIC = 7;

    private boolean blast = false, smoker = false, generic = false;

    public GuiNpcSmeltingRecipes(ContainerSmeltingRecipes container, Inventory inv, Component titleIn) {
        super(NoppesUtil.getLastNpc(), container, inv, titleIn);
        this.container = container;
        this.drawDefaultBackground = false;
        this.setBackground("inventorymenu.png");
        this.imageWidth = 420;
        this.imageHeight = 250;
        this.title = "";
        this.menuYOffset = 0;
        SmeltingPacketHandler.CHANNEL.sendToServer(new PacketSmeltingRequestList());
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        // 界面整体偏移：同时移动 CNPC 的 guiLeft/guiTop（背景板、顶部菜单条、我们的控件都用它）
        // 和 vanilla 的 leftPos/topPos（原版用它渲染槽位与物品）。两者一起动才不会错位。
        int ox = SmeltingLayout.guiOffsetX();
        int oy = SmeltingLayout.guiOffsetY();
        this.guiLeft += ox;
        this.guiTop += oy;
        this.leftPos = this.guiLeft;
        this.topPos = this.guiTop;
        // 顶部菜单条位置在 super.m_7856_() 里已按旧 guiTop 定好，用偏移后的值重新定位一次
        this.repositionMenu();

        boolean hasSelected = this.selectedId >= 0;

        if (this.scroll == null) {
            this.scroll = new GuiCustomScrollNop(this, 0);
        }
        this.scroll.setSize(130, 180);
        this.scroll.guiLeft = this.guiLeft + 172;
        this.scroll.guiTop = this.guiTop + 8;
        this.addScroll(this.scroll);

        // 配方名称输入框（左上，始终显示）
        this.addTextField(new GuiTextFieldNop(30, this,
                this.guiLeft + SmeltingLayout.nameX(), this.guiTop + SmeltingLayout.nameY(), 160, 20, this.nameText()));

        // 右侧按钮列（新建/移除/保存，始终显示）
        int bx = this.guiLeft + SmeltingLayout.btnX();
        int by = this.guiTop + SmeltingLayout.btnY();
        int bs = SmeltingLayout.btnSpacing();
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_NEW, bx, by, 84, 20, "gui.add"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_REMOVE, bx, by + bs, 84, 20, "gui.remove"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_SAVE, bx, by + bs * 2, 84, 20, "gui.save"));

        // 三个图标开关 + 时间/经验输入框：仅选中配方后显示
        if (hasSelected) {
            // 图标开关：无文字，物品图标提示用途，红框=禁用/绿框=启用
            int tw = SmeltingLayout.toggleWidth();
            int th = SmeltingLayout.toggleHeight();
            int tx = this.guiLeft + SmeltingLayout.toggleX();
            int ty = this.guiTop + SmeltingLayout.toggleY();
            int ts = SmeltingLayout.toggleSpacing();
            this.addButton(new GuiIconToggleButton(this, BTN_BLAST, tx, ty, tw, th,
                    new ItemStack(Blocks.BLAST_FURNACE), this.blast,
                    Component.translatable("cnpcplus.smelting.blast")));
            this.addButton(new GuiIconToggleButton(this, BTN_SMOKER, tx, ty + ts, tw, th,
                    new ItemStack(Blocks.SMOKER), this.smoker,
                    Component.translatable("cnpcplus.smelting.smoker")));
            this.addButton(new GuiIconToggleButton(this, BTN_GENERIC, tx, ty + ts * 2, tw, th,
                    new ItemStack(Items.COAL), this.generic,
                    Component.translatable("cnpcplus.smelting.generic")));

            // 时间/经验输入框：不放文字标签，鼠标悬停时显示这是「熔炼时间」还是「熔炼经验」
            int fw = SmeltingLayout.fieldWidth();
            int fh = SmeltingLayout.fieldHeight();
            int timeX = this.guiLeft + SmeltingLayout.timeX();
            int timeY = this.guiTop + SmeltingLayout.timeY();
            this.addTextField(new GuiTextFieldNop(20, this, timeX, timeY, fw, fh, "" + this.cookTimeText()));
            this.getTextField(20).floatsOnly = true;
            this.getTextField(20).setMinMaxDefault(0.01f, 100000.0f, 200.0f);
            int xpX = this.guiLeft + SmeltingLayout.xpX();
            int xpY = this.guiTop + SmeltingLayout.xpY();
            this.addTextField(new GuiTextFieldNop(21, this, xpX, xpY, fw, fh, "" + this.xpText()));
            this.getTextField(21).floatsOnly = true;
            this.getTextField(21).setMinMaxDefault(-100000.0f, 100000.0f, 0.0f);
        }

        this.refreshScroll();
    }

    /** 输入框悬停提示：id → 语言键。 */
    private static final int[] TIP_FIELDS = {20, 21};
    private static final String[] TIP_KEYS = {"cnpcplus.smelting.cooktime", "cnpcplus.smelting.xp"};

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (this.hasSubGui()) return;
        // 时间/经验输入框没有文字标签，悬停时说明它是哪个（父类渲染完再画，保证浮在最上层）
        for (int i = 0; i < TIP_FIELDS.length; i++) {
            GuiTextFieldNop tf = this.getTextField(TIP_FIELDS[i]);
            if (tf == null) continue;
            if (mouseX >= tf.getX() && mouseX < tf.getX() + tf.getWidth()
                    && mouseY >= tf.getY() && mouseY < tf.getY() + tf.getHeight()) {
                noppes.npcs.client.gui.util.GuiTooltipUtils.renderTooltip(
                        graphics, this.font, Component.translatable(TIP_KEYS[i]), mouseX, mouseY);
                break;
            }
        }
    }

    /**
     * 顶部菜单条（8 个按钮）位置由基类 GuiContainerNPCInterface2 用 guiTop 定死，
     * 我们改了 guiTop 之后必须让它按新值重排，否则菜单条留在原处。
     * 基类的 menu 字段是 private，用反射调一次 initGui。
     */
    private void repositionMenu() {
        try {
            java.lang.reflect.Field f = GuiContainerNPCInterface2.class.getDeclaredField("menu");
            f.setAccessible(true);
            Object menu = f.get(this);
            if (menu == null) return;
            java.lang.reflect.Method m = menu.getClass().getMethod("initGui", int.class, int.class, int.class);
            m.invoke(menu, this.guiLeft, this.guiTop + this.menuYOffset, this.imageWidth);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus")
                    .error("熔炼界面顶部菜单重定位失败（界面整体偏移将不影响菜单条）", e);
        }
    }

    private String nameText() {
        SmeltingRecipeData d = SmeltingClientData.byId(this.selectedId);
        return d == null ? "" : (d.name == null ? "" : d.name);
    }

    private String cookTimeText() {
        SmeltingRecipeData d = SmeltingClientData.byId(this.selectedId);
        return d == null ? "200.0" : "" + d.cookTime;
    }

    private String xpText() {
        SmeltingRecipeData d = SmeltingClientData.byId(this.selectedId);
        return d == null ? "0.0" : "" + d.xp;
    }

    private void refreshScroll() {
        this.recipes.clear();
        this.recipes.addAll(SmeltingClientData.get());
        List<String> names = new ArrayList<>();
        for (SmeltingRecipeData d : this.recipes) names.add(d.name);
        this.scroll.setList(names);
        if (this.selectedName != null) {
            this.scroll.setSelected(this.selectedName);
        } else if (!names.isEmpty()) {
            this.scroll.setSelected(names.get(0));
            this.selectedName = names.get(0);
            this.loadRecipe(0);
        }
    }

    private void loadRecipe(int index) {
        if (index < 0 || index >= this.recipes.size()) {
            this.selectedId = -1;
            this.blast = this.smoker = this.generic = false;
            if (this.getTextField(30) != null) this.getTextField(30).setValue("");
            if (this.getTextField(20) != null) this.getTextField(20).setValue("200.0");
            if (this.getTextField(21) != null) this.getTextField(21).setValue("0.0");
            return;
        }
        SmeltingRecipeData d = this.recipes.get(index);
        this.selectedId = d.id;
        this.selectedName = d.name;
        this.blast = d.blastAllowed;
        this.smoker = d.smokerAllowed;
        this.generic = d.genericFuelAllowed;
        if (this.getTextField(30) != null) this.getTextField(30).setValue(d.name == null ? "" : d.name);
        if (this.getTextField(20) != null) this.getTextField(20).setValue("" + d.cookTime);
        if (this.getTextField(21) != null) this.getTextField(21).setValue("" + d.xp);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == BTN_NEW) {
            this.selectedId = -1;
            this.selectedName = "new" + System.currentTimeMillis() % 100000;
            this.blast = this.smoker = this.generic = false;
            if (this.getTextField(30) != null) this.getTextField(30).setValue(this.selectedName);
            // 由服务端权威清空容器槽位（避免客户端本地 setItem 与点击包 stateId 冲突导致物品消失）
            SmeltingPacketHandler.CHANNEL.sendToServer(new PacketSmeltingSelect(-1));
            this.m_7856_();
        } else if (button.id == BTN_REMOVE) {
            if (this.selectedId >= 0) {
                SmeltingPacketHandler.CHANNEL.sendToServer(new PacketSmeltingRemove(this.selectedId));
                this.selectedId = -1;
                this.selectedName = null;
                SmeltingPacketHandler.CHANNEL.sendToServer(new PacketSmeltingRequestList());
            }
        } else if (button.id == BTN_SAVE) {
            this.saveRecipe();
        } else if (button.id == BTN_BLAST) {
            this.blast = button.getValue() == 1;
        } else if (button.id == BTN_SMOKER) {
            this.smoker = button.getValue() == 1;
        } else if (button.id == BTN_GENERIC) {
            this.generic = button.getValue() == 1;
        }
    }

    private void saveRecipe() {
        SmeltingRecipeData d = new SmeltingRecipeData();
        d.id = this.selectedId;
        String name = this.getTextField(30) != null ? this.getTextField(30).getValue() : "";
        d.name = (name == null || name.trim().isEmpty()) ? "new" : name.trim();
        if (d.name.equals("new") && d.id < 0) {
            d.name = "new" + System.currentTimeMillis() % 10000;
        }
        d.input = this.container.getInput().copy();
        d.fuel = this.container.getFuel().copy();
        d.output = this.container.getOutput().copy();
        d.blastAllowed = this.blast;
        d.smokerAllowed = this.smoker;
        d.genericFuelAllowed = this.generic;
        if (this.getTextField(20) != null) {
            try { d.cookTime = Math.max(0.01f, Float.parseFloat(this.getTextField(20).getValue())); }
            catch (Exception e) { d.cookTime = 200.0f; }
        } else d.cookTime = 200.0f;
        if (this.getTextField(21) != null) {
            try { d.xp = Float.parseFloat(this.getTextField(21).getValue()); }
            catch (Exception e) { d.xp = 0.0f; }
        } else d.xp = 0.0f;
        SmeltingPacketHandler.CHANNEL.sendToServer(new PacketSmeltingSave(d.toNBT()));
    }

    @Override
    public void scrollClicked(double i, double j, int k, GuiCustomScrollNop guiCustomScroll) {
        if (this.scroll.getSelected() == null) return;
        this.selectedName = this.scroll.getSelected();
        for (int n = 0; n < this.recipes.size(); n++) {
            if (this.recipes.get(n).name.equals(this.selectedName)) {
                this.loadRecipe(n);
                break;
            }
        }
        if (this.selectedId >= 0) {
            SmeltingPacketHandler.CHANNEL.sendToServer(new PacketSmeltingSelect(this.selectedId));
        }
        this.m_7856_();
    }

    /** 收到服务端配方列表同步后刷新（由 PacketSmeltingSync 客户端处理调用）。 */
    public void refreshFromServer() {
        this.m_7856_();
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
    }

    @Override
    protected void m_7286_(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        super.m_7286_(graphics, partialTicks, mouseX, mouseY);
        // 界面整体偏移已在 m_7856_ 里加进 guiLeft/guiTop 与 leftPos/topPos，这里不再另加。

        // 三个槽位背景：直接读容器里实际槽位的真实坐标（-1 让 18x18 外框套住 16x16 槽位），永不错位
        drawSlotBgAt(graphics, ContainerSmeltingRecipes.SLOT_INDEX_INPUT);
        drawSlotBgAt(graphics, ContainerSmeltingRecipes.SLOT_INDEX_FUEL);
        drawSlotBgAt(graphics, ContainerSmeltingRecipes.SLOT_INDEX_OUTPUT);

        // 火焰：原版贴图 (176,0) 14x14，动态高度 k=0..13（模拟燃烧跳动）
        int flameX = this.guiLeft + SmeltingLayout.flameX();
        int flameY = this.guiTop + SmeltingLayout.flameY();
        int k = (Minecraft.getInstance().gui.getGuiTicks() / 2) % 14;
        graphics.blit(FURNACE_TEX, flameX, flameY + 12 - k, 176, 12 - k, 14, k + 1);

        // 熔炼进度箭头：原版贴图 (176,14) 24x17，动态宽度 l=0..23（按当前配方 cookTime 循环填充）
        int arrowX = this.guiLeft + SmeltingLayout.arrowX();
        int arrowY = this.guiTop + SmeltingLayout.arrowY();
        int cookTicks = Math.max(20, Math.round(this.cookTimeValue()));
        int l = (Minecraft.getInstance().gui.getGuiTicks() * 2) % cookTicks * 24 / cookTicks;
        graphics.blit(FURNACE_TEX, arrowX, arrowY, 176, 14, l + 1, 16);
    }

    /** 按容器里实际槽位的真实坐标画 18x18 背景框（-1 偏移让外框正好套住 16x16 槽位）。 */
    private void drawSlotBgAt(GuiGraphics graphics, int slotIndex) {
        int sx = this.container.slotRenderX(slotIndex);
        int sy = this.container.slotRenderY(slotIndex);
        graphics.blit(SLOT_TEX, this.leftPos + sx - 1, this.topPos + sy - 1, 0, 0, 18, 18);
    }

    private float cookTimeValue() {
        SmeltingRecipeData d = SmeltingClientData.byId(this.selectedId);
        return d == null ? 200.0f : d.cookTime;
    }
}