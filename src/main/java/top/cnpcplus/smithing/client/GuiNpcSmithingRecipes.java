package top.cnpcplus.smithing.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.cnpcplus.smelting.client.GuiIconToggleButton;
import top.cnpcplus.smithing.ContainerSmithingRecipes;
import top.cnpcplus.smithing.SmithingLayout;
import top.cnpcplus.smithing.SmithingRecipeData;
import top.cnpcplus.smithing.network.PacketSmithingRemove;
import top.cnpcplus.smithing.network.PacketSmithingRequestList;
import top.cnpcplus.smithing.network.PacketSmithingSave;
import top.cnpcplus.smithing.network.PacketSmithingSelect;
import top.cnpcplus.smithing.network.SmithingPacketHandler;

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
 * 可视化自定义锻造配方编辑界面（阶段226 从 GuiNpcSmeltingRecipes 整界面复制，227 接通全部数据链路）：
 * 1. 槽位从熔炉改锻造台：4 槽（模板/基座/升级材料/结果）紧邻行+右侧结果，坐标见 SmithingLayout；
 *    火焰/进度箭头动画随之删除（熔炉区装饰，不属于锻造台）。
 * 2. 「相对功能开关」：高炉/烟熏炉/通用燃料 3 个不复制（不属于锻造台）；
 *    230 加回「仅名字检查」1 个（照抄熔炼 GuiIconToggleButton 外观：命名牌图标+红绿框+悬停名）。
 * 3. 227（用户第2步）：熔炼时间/经验两个输入框及其悬停提示删除（锻造台没有时间与经验）；
 *    锻造台锤子/箭头贴图绘制进面板；新建/移除/保存/选中全部接通真实数据包。
 *    保存 = 注入 RecipeManager 热添加（原版锻造台下一次操作即可用），删除同理反向生效。
 *    进入铁律：只在 SmithingClientData 为空时才发 RequestList（缓存非空直接本地渲染，不顿）。
 */
public class GuiNpcSmithingRecipes extends GuiContainerNPCInterface2<ContainerSmithingRecipes>
        implements ICustomScrollListener {

    private static final ResourceLocation SLOT_TEX =
            new ResourceLocation("customnpcs", "textures/gui/slot.png");
    private static final ResourceLocation SMITHING_TEX =
            new ResourceLocation("minecraft", "textures/gui/container/smithing.png");

    private GuiCustomScrollNop scroll;
    private final ContainerSmithingRecipes container;
    private final List<SmithingRecipeData> recipes = new ArrayList<>();
    private String selectedName = null;
    private int selectedId = -1;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private static final int BTN_NEW = 3;
    private static final int BTN_REMOVE = 4;
    private static final int BTN_SAVE = 2;
    private static final int BTN_NAMEONLY = 5;

    /** 「新建草稿」状态：点过「新建」但还没保存（沿用熔炼版机制，防表单死锁）。 */
    private boolean draft = false;

    /** 230 仅名字检查：保存时随配方落盘（只校验物品id+物品名字，开关外观照抄熔炼）。 */
    private boolean nameOnly = false;

    public GuiNpcSmithingRecipes(ContainerSmithingRecipes container, Inventory inv, Component titleIn) {
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
        boolean cacheEmpty = SmithingClientData.get().isEmpty();
        if (cacheEmpty) {
            SmithingPacketHandler.CHANNEL.sendToServer(new PacketSmithingRequestList());
        }
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        // 界面整体偏移：guiLeft/guiTop 与 vanilla leftPos/topPos 一起按居中基准+偏移重算，
        // 不用 += 累加（m_7856_ 被二次调用时会叠加跑偏——熔炼版已实证，见 SmeltingLayout 注释）。
        this.guiLeft = (this.width - this.imageWidth) / 2 + SmithingLayout.guiOffsetX();
        this.guiTop = (this.height - this.imageHeight) / 2 + SmithingLayout.guiOffsetY();
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

        // 配方名称输入框（左上，始终显示）
        this.addTextField(new GuiTextFieldNop(30, this,
                this.guiLeft + SmithingLayout.nameX(), this.guiTop + SmithingLayout.nameY(), 160, 20, this.nameText()));

        // 右侧按钮列（新建/移除/保存，始终显示）
        int bx = this.guiLeft + SmithingLayout.btnX();
        int by = this.guiTop + SmithingLayout.btnY();
        int bs = SmithingLayout.btnSpacing();
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_NEW, bx, by, 84, 20, "gui.add"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_REMOVE, bx, by + bs, 84, 20, "gui.remove"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_SAVE, bx, by + bs * 2, 84, 20, "gui.save"));

        // 230 「仅名字检查」开关：照抄熔炼外观（命名牌图标+红绿框+悬停显示「仅名字检查」），
        // 位置=三个输入槽的左边（坐标见 SmithingLayout.nameOnlyX/Y，避开锤子贴图）。
        // 与槽位同生命周期：选中配方或草稿态都显示（草稿也要能配此开关）。
        // 文案键用 cnpcplus 独占的 cnpcplus.nameonly——原 gui.ignoreNBT 被 CNPC 本体同键
        // 「忽略NBT值」按 namespace 字母序覆盖（cnpcplus < customnpcs 后写者赢），游戏内显示错文案。
        if (this.draft || this.selectedId >= 0) {
            this.addButton(new GuiIconToggleButton(this, BTN_NAMEONLY,
                    this.guiLeft + SmithingLayout.nameOnlyX(), this.guiTop + SmithingLayout.nameOnlyY(),
                    SmithingLayout.nameOnlyWidth(), SmithingLayout.nameOnlyHeight(),
                    new ItemStack(Items.NAME_TAG), this.nameOnly,
                    Component.translatable("cnpcplus.nameonly")));
        }

        this.refreshScroll();
    }

    /** 保存失败等提示语（画在按钮列下方，null 表示不显示）。 */
    private String notice = null;

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (this.hasSubGui()) return;
        if (this.notice != null) {
            graphics.drawString(this.font, this.notice,
                    this.guiLeft + SmithingLayout.btnX(),
                    this.guiTop + SmithingLayout.btnY() + SmithingLayout.btnSpacing() * 3 + 6,
                    0xFFFF5555, false);
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
                    .error("锻造界面顶部菜单重定位失败（界面整体偏移将不影响菜单条）", e);
        }
    }

    private String nameText() {
        // 228 修「点添加无反应」：草稿态显示草稿名。nameText() 只看 selectedId（草稿=-1→空串），
        // 会把 buttonEvent 里 setValue 的 new<时间戳> 在 m_7856_ 重建文本框时覆盖掉，
        // 造成名称框/列表/槽位全无变化的"零视觉反馈"。
        if (this.draft && this.selectedName != null) return this.selectedName;
        SmithingRecipeData d = SmithingClientData.byId(this.selectedId);
        return d == null ? "" : (d.name == null ? "" : d.name);
    }

    private void refreshScroll() {
        this.recipes.clear();
        this.recipes.addAll(SmithingClientData.get());
        List<String> names = new ArrayList<>();
        for (SmithingRecipeData d : this.recipes) names.add(d.name);
        this.scroll.setList(names);
        if (this.selectedName != null) {
            this.scroll.setSelected(this.selectedName);
        } else if (!names.isEmpty()) {
            this.scroll.setSelected(names.get(0));
            this.selectedName = names.get(0);
            this.loadRecipe(0);
            // 首开自动选中第一条：同时让服务端把该配方填进四槽（新容器此时必为空槽，
            // 填入即「列表选中项=槽内展示」状态一致；服务端会先归还槽内原有物品，不会销毁）。
            if (this.selectedId >= 0) {
                SmithingPacketHandler.CHANNEL.sendToServer(new PacketSmithingSelect(this.selectedId));
            }
        }
    }

    private void loadRecipe(int index) {
        if (index < 0 || index >= this.recipes.size()) {
            this.selectedId = -1;
            this.draft = false;
            this.nameOnly = false;
            if (this.getTextField(30) != null) this.getTextField(30).setValue("");
            return;
        }
        SmithingRecipeData d = this.recipes.get(index);
        this.selectedId = d.id;
        this.selectedName = d.name;
        this.nameOnly = d.nameOnly;
        // 选中列表里的正式配方 = 草稿结束（否则保存后 refreshFromServer 仍按草稿名匹配，
        // 且 nameText 的草稿分支会一直生效）
        this.draft = false;
        if (this.getTextField(30) != null) this.getTextField(30).setValue(d.name == null ? "" : d.name);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == BTN_NEW) {
            // 「添加配方」= 清空表单 + 清空四个槽 + 进入草稿 + 自动生成随机名字。
            // 清槽由服务端权威执行（PacketSmithingSelect(-1) 查不到配方时把四槽设为 EMPTY 并归还物品），
            // 客户端不自己 setItem，避免与点击包的 stateId 对不上导致物品显示错乱。
            SmithingPacketHandler.CHANNEL.sendToServer(new PacketSmithingSelect(-1));
            this.selectedId = -1;
            this.draft = true;
            this.nameOnly = false;
            this.selectedName = "new" + System.currentTimeMillis() % 100000;
            if (this.getTextField(30) != null) this.getTextField(30).setValue(this.selectedName);
            this.m_7856_();
        } else if (button.id == BTN_REMOVE) {
            if (this.selectedId >= 0) {
                SmithingPacketHandler.CHANNEL.sendToServer(new PacketSmithingRemove(this.selectedId));
                this.selectedId = -1;
                this.draft = false;
                this.nameOnly = false;
                this.selectedName = null;
                // 服务端删除后会主动推 Sync（列表+热移除都由它收口），无需再手动 RequestList。
            }
        } else if (button.id == BTN_SAVE) {
            this.saveRecipe();
        } else if (button.id == BTN_NAMEONLY) {
            this.nameOnly = button.getValue() == 1;
        }
    }

    private void saveRecipe() {
        // 客户端先自检（228 放宽，与服务端同规则）：三输入槽至少一个非空 + 结果槽必放。
        // 不满足时服务端会静默 return（与熔炼 needio 同款坑），这里直接给出可见提示，
        // 别让玩家对着没反应的按钮猜。
        boolean anyInput = !this.container.getTemplate().isEmpty() || !this.container.getBase().isEmpty()
                || !this.container.getAddition().isEmpty();
        if (!anyInput || this.container.getOutput().isEmpty()) {
            this.notice = Component.translatable("cnpcplus.smithing.need4").getString();
            LOGGER.warn("[smithing] 保存被客户端预检拦截: t={} b={} a={} r={} (规则=输入至少一且结果必放)",
                    !this.container.getTemplate().isEmpty(), !this.container.getBase().isEmpty(),
                    !this.container.getAddition().isEmpty(), !this.container.getOutput().isEmpty());
            return;
        }
        this.notice = null;
        SmithingRecipeData d = new SmithingRecipeData();
        d.id = this.selectedId;
        d.nameOnly = this.nameOnly;
        String name = this.getTextField(30) != null ? this.getTextField(30).getValue() : "";
        d.name = (name == null || name.trim().isEmpty()) ? "new" : name.trim();
        if (d.name.equals("new") && d.id < 0) {
            d.name = "new" + System.currentTimeMillis() % 10000;
        }
        // 四槽物品刻意不带进包里：服务端只认它自己容器里的 ItemStack（见 PacketSmithingSave），
        // 客户端发过去的会被直接丢弃。而 C→S 自定义载荷硬上限是 32767 字节（javap 实证
        // ServerboundCustomPayloadPacket 读取时超限直接抛 IllegalArgumentException → 玩家被踢），
        // 槽里放个装满物品的潜影盒或大 NBT 物品就可能撞线。既然是废数据，就别发。
        // 名称同步进 selectedName：refreshFromServer 靠它按「最终保存名」匹配草稿收尾，
        // 否则玩家在名称框改过名后匹配不上 → draft 不结束 → 再点保存会重复新建同名配方。
        this.selectedName = d.name;
        SmithingPacketHandler.CHANNEL.sendToServer(new PacketSmithingSave(d.toNBT(false)));
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
            SmithingPacketHandler.CHANNEL.sendToServer(new PacketSmithingSelect(this.selectedId));
        }
        this.m_7856_();
    }

    /**
     * 收到服务端配方列表同步后刷新（227 已由 SmithingClientSync.accept 调用）。
     * 保存成功会触发同步下发，此时草稿已经变成正式配方，按名字把它选中，草稿状态收尾。
     */
    public void refreshFromServer() {
        if (this.draft && this.selectedName != null) {
            for (SmithingRecipeData d : SmithingClientData.get()) {
                if (this.selectedName.equals(d.name)) {
                    this.selectedId = d.id;
                    this.draft = false;
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

        // 四个锻造台槽位背景：直接读容器实际槽位真实坐标（-1 让 18x18 框套住 16x16 槽位）
        drawSlotBgAt(graphics, ContainerSmithingRecipes.SLOT_INDEX_TEMPLATE);
        drawSlotBgAt(graphics, ContainerSmithingRecipes.SLOT_INDEX_BASE);
        drawSlotBgAt(graphics, ContainerSmithingRecipes.SLOT_INDEX_ADDITION);
        drawSlotBgAt(graphics, ContainerSmithingRecipes.SLOT_INDEX_OUTPUT);
        // 226：熔炼版的火焰/进度箭头动画是熔炉区装饰，锻造台不需要，已删。
        // 227：换上锻造台自己的两张静态装饰（原版 smithing.png 子区域，像素扫描实证 uv）——
        // 锤子：时间/经验两框删除后空出的左区；箭头：升级材料槽与结果槽之间 36px 缝隙。
        graphics.blit(SMITHING_TEX,
                this.guiLeft + SmithingLayout.hammerX(), this.guiTop + SmithingLayout.hammerY(),
                7, 8, 30, 30);
        graphics.blit(SMITHING_TEX,
                this.guiLeft + SmithingLayout.arrowX(), this.guiTop + SmithingLayout.arrowY(),
                68, 49, 22, 15);
    }

    /** 按容器里实际槽位的真实坐标画 18x18 背景框。 */
    private void drawSlotBgAt(GuiGraphics graphics, int slotIndex) {
        int sx = this.container.slotRenderX(slotIndex);
        int sy = this.container.slotRenderY(slotIndex);
        graphics.blit(SLOT_TEX, this.leftPos + sx - 1, this.topPos + sy - 1, 0, 0, 18, 18);
    }
}
