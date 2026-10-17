package top.cnpcplus.anvil.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.cnpcplus.anvil.AnvilCraftHandler;
import top.cnpcplus.anvil.AnvilLayout;
import top.cnpcplus.anvil.AnvilRecipeData;
import top.cnpcplus.anvil.ContainerAnvilRecipes;
import top.cnpcplus.anvil.network.AnvilPacketHandler;
import top.cnpcplus.anvil.network.PacketAnvilRemove;
import top.cnpcplus.anvil.network.PacketAnvilRequestList;
import top.cnpcplus.anvil.network.PacketAnvilSave;
import top.cnpcplus.anvil.network.PacketAnvilSelect;
import top.cnpcplus.smelting.client.GuiIconToggleButton;

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
 * 可视化自定义铁砧配方编辑界面（阶段231 从 GuiNpcSmithingRecipes 复制改三槽+铁砧装饰）：
 * 1. 三槽按 vanilla 铁砧相对位置 27/76/134 拉开，槽间贴 anvil.png 的 plus/arrow 透明裁片（231b 反馈）；
 * 2. 左上装饰=大白锤子（anvil.png 裁片，231b 反馈，替换铁砧物品图标）+ 名称框右移让位；
 * 3. 经验输入框在结果槽下方 (134,75) 宽36（231b：原40太长/位置改），
 *    范围 -32767..32767（负数=给锻造者经验，mayPickup 由 MixinAnvilMenu 放宽）；
 * 4. 「仅名字检查」开关结果槽右边 (152,51)（230 同款 GuiIconToggleButton，键 cnpcplus.nameonly 独占）；
 * 5. 保存=注册进 AnvilRecipeRegistry+全员 Sync（不注入 RecipeManager——AnvilCraftHandler 事件直查生效）。
 *    进入铁律：只在 AnvilClientData 为空时才发 RequestList（缓存非空直接本地渲染，不顿）。
 * 槽位与 UI 教训沿用：guiLeft/guiTop 居中基准+偏移重算（禁 += 累加）、槽背景按容器真实坐标画。
 */
public class GuiNpcAnvilRecipes extends GuiContainerNPCInterface2<ContainerAnvilRecipes>
        implements ICustomScrollListener {

    private static final ResourceLocation SLOT_TEX =
            new ResourceLocation("customnpcs", "textures/gui/slot.png");
    /** anvil.png 透明裁片（231b：由原版 anvil.png 导出，198 底转透明，防 blit 色块） */
    private static final ResourceLocation HAMMER_TEX =
            new ResourceLocation("cnpcplus", "textures/gui/anvil_hammer.png");
    private static final ResourceLocation PLUS_TEX =
            new ResourceLocation("cnpcplus", "textures/gui/anvil_plus.png");
    private static final ResourceLocation ARROW_TEX =
            new ResourceLocation("cnpcplus", "textures/gui/anvil_arrow.png");

    private GuiCustomScrollNop scroll;
    private final ContainerAnvilRecipes container;
    private final List<AnvilRecipeData> recipes = new ArrayList<>();
    private String selectedName = null;
    private int selectedId = -1;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private static final int BTN_NEW = 3;
    private static final int BTN_REMOVE = 4;
    private static final int BTN_SAVE = 2;
    private static final int BTN_NAMEONLY = 5;
    private static final int FIELD_EXP = 31;

    /** 「新建草稿」状态：点过「新建」但还没保存（沿用熔炼/锻造机制，防表单死锁）。 */
    private boolean draft = false;

    /** 230 仅名字检查（铁砧同款）：保存时随配方落盘。 */
    private boolean nameOnly = false;

    /** 敲打一次消耗经验（-32767..32767，负数=给锻造者；新配方默认1），来自经验输入框/选中配方。 */
    private int expCost = 1;

    public GuiNpcAnvilRecipes(ContainerAnvilRecipes container, Inventory inv, Component titleIn) {
        super(NoppesUtil.getLastNpc(), container, inv, titleIn);
        this.container = container;
        this.drawDefaultBackground = false;
        this.setBackground("inventorymenu.png");
        this.imageWidth = 420;
        this.imageHeight = 250;
        this.title = "";
        this.menuYOffset = 0;
        // 进入铁律（用户「每次进去不许顿一下」）：缓存非空 = 直接本地渲染，一个包都不发；
        // 只在首次（缓存为空）向服务端要一次全量列表，收到后 Sync 会回调 refreshFromServer。
        boolean cacheEmpty = AnvilClientData.get().isEmpty();
        if (cacheEmpty) {
            AnvilPacketHandler.CHANNEL.sendToServer(new PacketAnvilRequestList());
        }
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        // 界面整体偏移：guiLeft/guiTop 与 vanilla leftPos/topPos 一起按居中基准+偏移重算，
        // 不用 += 累加（m_7856_ 被二次调用时会叠加跑偏——熔炼版已实证，见 SmeltingLayout 注释）。
        this.guiLeft = (this.width - this.imageWidth) / 2 + AnvilLayout.guiOffsetX();
        this.guiTop = (this.height - this.imageHeight) / 2 + AnvilLayout.guiOffsetY();
        this.leftPos = this.guiLeft;
        this.topPos = this.guiTop;
        this.repositionMenu();

        if (this.scroll == null) {
            this.scroll = new GuiCustomScrollNop(this, 0);
        }
        this.scroll.setSize(130, 180);
        this.scroll.guiLeft = this.guiLeft + 172;
        this.scroll.guiTop = this.guiTop + 8;
        this.addScroll(this.scroll);

        // 配方名称输入框（上部，锤子右侧让位后 x42 宽126，始终显示）
        this.addTextField(new GuiTextFieldNop(30, this,
                this.guiLeft + AnvilLayout.nameX(), this.guiTop + AnvilLayout.nameY(),
                AnvilLayout.nameWidth(), 20, this.nameText()));

        // 右侧按钮列（新建/移除/保存，始终显示）
        int bx = this.guiLeft + AnvilLayout.btnX();
        int by = this.guiTop + AnvilLayout.btnY();
        int bs = AnvilLayout.btnSpacing();
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_NEW, bx, by, 84, 20, "gui.add"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_REMOVE, bx, by + bs, 84, 20, "gui.remove"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_SAVE, bx, by + bs * 2, 84, 20, "gui.save"));

        // 经验输入框 + 仅名字检查开关：与槽位同生命周期（选中配方或草稿态都显示）
        if (this.draft || this.selectedId >= 0) {
            // 经验输入框（敲打一次消耗多少经验）：整数 -32767..32767，负数=给锻造者经验
            // （可取走由 MixinAnvilMenu 放宽 mayPickup 的 cost>0 实现；默认值=1）
            this.addTextField(new GuiTextFieldNop(FIELD_EXP, this,
                    this.guiLeft + AnvilLayout.expFieldX(), this.guiTop + AnvilLayout.expFieldY(),
                    AnvilLayout.expFieldWidth(), AnvilLayout.expFieldHeight(), "" + this.expCost));
            this.getTextField(FIELD_EXP).setMinMaxDefault(
                    AnvilCraftHandler.EXP_MIN, AnvilCraftHandler.EXP_MAX, 1);
            // 「仅名字检查」开关：放结果槽右边（用户指定），外观照抄熔炼（命名牌图标+红绿框+悬停名）。
            // 文案键用 cnpcplus 独占的 cnpcplus.nameonly（230b 键竞争教训：gui.ignoreNBT 被本体覆盖）。
            this.addButton(new GuiIconToggleButton(this, BTN_NAMEONLY,
                    this.guiLeft + AnvilLayout.nameOnlyX(), this.guiTop + AnvilLayout.nameOnlyY(),
                    AnvilLayout.nameOnlyWidth(), AnvilLayout.nameOnlyHeight(),
                    new ItemStack(Items.NAME_TAG), this.nameOnly,
                    Component.translatable("cnpcplus.nameonly")));
        }

        this.refreshScroll();
    }

    /** 保存失败等提示语（画在按钮列下方，null 表示不显示）。 */
    private String notice = null;

    /** 输入框悬停提示：id → 语言键。 */
    private static final int[] TIP_FIELDS = {FIELD_EXP};
    private static final String[] TIP_KEYS = {"cnpcplus.anvil.expcost"};

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (this.hasSubGui()) return;
        if (this.notice != null) {
            graphics.drawString(this.font, this.notice,
                    this.guiLeft + AnvilLayout.btnX(),
                    this.guiTop + AnvilLayout.btnY() + AnvilLayout.btnSpacing() * 3 + 6,
                    0xFFFF5555, false);
        }
        // 经验输入框没有文字标签，悬停时说明它是哪个（父类渲染完再画，保证浮在最上层）
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
     * 顶部菜单条位置由基类用 guiTop 定死，改了 guiTop 后必须按新值重排（反射调一次 initGui，与熔炼版相同）。
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
                    .error("铁砧界面顶部菜单重定位失败（界面整体偏移将不影响菜单条）", e);
        }
    }

    private String nameText() {
        // 草稿态显示草稿名（228「点添加无反应」同款修复：nameText 只看 selectedId 会覆盖草稿名）
        if (this.draft && this.selectedName != null) return this.selectedName;
        AnvilRecipeData d = AnvilClientData.byId(this.selectedId);
        return d == null ? "" : (d.name == null ? "" : d.name);
    }

    private void refreshScroll() {
        this.recipes.clear();
        this.recipes.addAll(AnvilClientData.get());
        List<String> names = new ArrayList<>();
        for (AnvilRecipeData d : this.recipes) names.add(d.name);
        this.scroll.setList(names);
        if (this.selectedName != null) {
            this.scroll.setSelected(this.selectedName);
        } else if (!names.isEmpty()) {
            this.scroll.setSelected(names.get(0));
            this.selectedName = names.get(0);
            this.loadRecipe(0);
            // 首开自动选中第一条：同时让服务端把该配方填进三槽（新容器此时必为空槽，
            // 填入即「列表选中项=槽内展示」状态一致；服务端会先归还槽内原有物品，不会销毁）。
            if (this.selectedId >= 0) {
                AnvilPacketHandler.CHANNEL.sendToServer(new PacketAnvilSelect(this.selectedId));
            }
        }
    }

    private void loadRecipe(int index) {
        if (index < 0 || index >= this.recipes.size()) {
            this.selectedId = -1;
            this.draft = false;
            this.nameOnly = false;
            this.expCost = 1;
            if (this.getTextField(30) != null) this.getTextField(30).setValue("");
            if (this.getTextField(FIELD_EXP) != null) this.getTextField(FIELD_EXP).setValue("1");
            return;
        }
        AnvilRecipeData d = this.recipes.get(index);
        this.selectedId = d.id;
        this.selectedName = d.name;
        this.nameOnly = d.nameOnly;
        this.expCost = d.expCost;
        // 选中列表里的正式配方 = 草稿结束（否则保存后 refreshFromServer 仍按草稿名匹配）
        this.draft = false;
        if (this.getTextField(30) != null) this.getTextField(30).setValue(d.name == null ? "" : d.name);
        if (this.getTextField(FIELD_EXP) != null) this.getTextField(FIELD_EXP).setValue("" + d.expCost);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == BTN_NEW) {
            // 「添加配方」= 清空表单 + 清空三个槽 + 进入草稿 + 自动生成随机名字。
            // 清槽由服务端权威执行（PacketAnvilSelect(-1) 查不到配方时把三槽设为 EMPTY 并归还物品），
            // 客户端不自己 setItem，避免与点击包的 stateId 对不上导致物品显示错乱。
            AnvilPacketHandler.CHANNEL.sendToServer(new PacketAnvilSelect(-1));
            this.selectedId = -1;
            this.draft = true;
            this.nameOnly = false;
            this.expCost = 1;
            this.selectedName = "new" + System.currentTimeMillis() % 100000;
            if (this.getTextField(30) != null) this.getTextField(30).setValue(this.selectedName);
            if (this.getTextField(FIELD_EXP) != null) this.getTextField(FIELD_EXP).setValue("1");
            this.m_7856_();
        } else if (button.id == BTN_REMOVE) {
            if (this.selectedId >= 0) {
                AnvilPacketHandler.CHANNEL.sendToServer(new PacketAnvilRemove(this.selectedId));
                this.selectedId = -1;
                this.draft = false;
                this.nameOnly = false;
                this.expCost = 1;
                this.selectedName = null;
                // 服务端删除后会主动推 Sync（列表都由它收口），无需再手动 RequestList。
            }
        } else if (button.id == BTN_SAVE) {
            this.saveRecipe();
        } else if (button.id == BTN_NAMEONLY) {
            this.nameOnly = button.getValue() == 1;
        }
    }

    private void saveRecipe() {
        // 客户端先自检（与服务端 PacketAnvilSave 同规则）：A、B、结果三槽全部必放。
        // 不满足时服务端会静默 return（与熔炼 needio 同款坑），这里直接给出可见提示。
        if (this.container.getInputA().isEmpty() || this.container.getInputB().isEmpty()
                || this.container.getOutput().isEmpty()) {
            this.notice = Component.translatable("cnpcplus.anvil.need").getString();
            LOGGER.debug("[anvil] 保存被客户端预检拦截: a={} b={} r={} (规则=A/B/结果全必放)",
                    !this.container.getInputA().isEmpty(), !this.container.getInputB().isEmpty(),
                    !this.container.getOutput().isEmpty());
            return;
        }
        this.notice = null;
        AnvilRecipeData d = new AnvilRecipeData();
        d.id = this.selectedId;
        d.nameOnly = this.nameOnly;
        // 经验读输入框：未显示（无选中）兜底 1；越界由保存末尾统一夹（范围 -32767..32767）。
        int exp = 1;
        GuiTextFieldNop expField = this.getTextField(FIELD_EXP);
        if (expField != null) {
            try {
                exp = (int) Float.parseFloat(expField.getValue());
            } catch (NumberFormatException ignored) {
                exp = 1;
            }
        }
        d.expCost = Math.max(AnvilCraftHandler.EXP_MIN, Math.min(AnvilCraftHandler.EXP_MAX, exp));
        // 231b bug4 修复：内存字段同步为本次保存值——否则保存后 refreshFromServer→m_7856_
        // 用旧字段（默认1）重建经验输入框，界面显示被"重置回1"，再点保存还会把1存回去。
        this.expCost = d.expCost;
        String name = this.getTextField(30) != null ? this.getTextField(30).getValue() : "";
        d.name = (name == null || name.trim().isEmpty()) ? "new" : name.trim();
        if (d.name.equals("new") && d.id < 0) {
            d.name = "new" + System.currentTimeMillis() % 10000;
        }
        // 三槽物品刻意不带进包里：服务端只认它自己容器里的 ItemStack（见 PacketAnvilSave），
        // 客户端发过去的会被直接丢弃（C→S 32767 上限，潜影盒大 NBT 会撞线）。
        this.selectedName = d.name;
        AnvilPacketHandler.CHANNEL.sendToServer(new PacketAnvilSave(d.toNBT(false)));
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
            AnvilPacketHandler.CHANNEL.sendToServer(new PacketAnvilSelect(this.selectedId));
        }
        this.m_7856_();
    }

    /**
     * 收到服务端配方列表同步后刷新（AnvilClientSync.accept 调用）。
     * 保存成功会触发同步下发，此时草稿已经变成正式配方，按名字把它选中，草稿状态收尾。
     */
    public void refreshFromServer() {
        if (this.draft && this.selectedName != null) {
            for (int i = 0; i < AnvilClientData.get().size(); i++) {
                AnvilRecipeData d = AnvilClientData.get().get(i);
                if (this.selectedName.equals(d.name)) {
                    // 231b bug4 修复：按服务端落盘后的真值整体回填（loadRecipe=选中id/经验/开关/
                    // 两个文本框一次到位），只回填 selectedId 会让经验输入框停在保存前的旧值。
                    this.loadRecipe(i);
                    break;
                }
            }
        }
        this.m_7856_();
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
    }

    @Override
    protected void m_7286_(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        super.m_7286_(graphics, partialTicks, mouseX, mouseY);

        // 三个铁砧槽位背景：直接读容器实际槽位真实坐标（-1 让 18x18 框套住 16x16 槽位）
        drawSlotBgAt(graphics, ContainerAnvilRecipes.SLOT_INDEX_A);
        drawSlotBgAt(graphics, ContainerAnvilRecipes.SLOT_INDEX_B);
        drawSlotBgAt(graphics, ContainerAnvilRecipes.SLOT_INDEX_OUTPUT);
        // 槽间贴图（231b 用户反馈=照原版铁砧）：A+B 之间加号、B→结果箭头（anvil.png 透明裁片）
        // 9 参 blit（带 texWidth/texHeight）：6 参版内部假设 256x256，小纹理会采到透明区整张不显示
        graphics.blit(PLUS_TEX,
                this.guiLeft + AnvilLayout.plusX(), this.guiTop + AnvilLayout.plusY(),
                0f, 0f, 13, 13, 13, 13);
        graphics.blit(ARROW_TEX,
                this.guiLeft + AnvilLayout.arrowX(), this.guiTop + AnvilLayout.arrowY(),
                0f, 0f, 22, 15, 22, 15);
        // 左上装饰 = 大白锤子（231b 用户反馈：要铁砧界面的大白锤子，非铁砧物品图标）
        graphics.blit(HAMMER_TEX,
                this.guiLeft + AnvilLayout.hammerX(), this.guiTop + AnvilLayout.hammerY(),
                0f, 0f, 30, 30, 30, 30);
    }

    /** 按容器里实际槽位的真实坐标画 18x18 背景框。 */
    private void drawSlotBgAt(GuiGraphics graphics, int slotIndex) {
        int sx = this.container.slotRenderX(slotIndex);
        int sy = this.container.slotRenderY(slotIndex);
        graphics.blit(SLOT_TEX, this.leftPos + sx - 1, this.topPos + sy - 1, 0, 0, 18, 18);
    }
}
