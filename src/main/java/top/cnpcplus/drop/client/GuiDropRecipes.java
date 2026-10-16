package top.cnpcplus.drop.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.cnpcplus.drop.DropLayout;
import top.cnpcplus.drop.DropRecipeData;
import top.cnpcplus.drop.ContainerDropRecipes;
import top.cnpcplus.drop.network.DropPacketHandler;
import top.cnpcplus.drop.network.PacketDropRemove;
import top.cnpcplus.drop.network.PacketDropRequestList;
import top.cnpcplus.drop.network.PacketDropSave;
import top.cnpcplus.drop.network.PacketDropSelect;

import noppes.npcs.client.NoppesUtil;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import top.cnpcplus.smelting.client.GuiIconToggleButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 可视化自定义掉落物配方编辑界面（阶段232 从 GuiNpcAnvilRecipes 复制改五槽）：
 * 1. 无锤子装饰（用户指定）：2×2 紧凑输入 + arrow + 结果，箭头复用 anvil_arrow.png 透明裁片；
 * 2. 名称框回到最左 (8,8) 宽 158（没有锤子让位）；
 * 3. 无经验框、无开关（nameOnly 字段 data 先留，GUI 本轮不放——用户后续要改）；
 * 4. 保存=注册进 DropRecipeRegistry+全员 Sync；注册条件=≥2 输入槽+结果（客户端预检+服务端同规则）；
 *    进入铁律：只在 DropClientData 为空时才发 RequestList（缓存非空直接本地渲染，不顿）。
 * 槽位与 UI 教训沿用：guiLeft/guiTop 居中基准+偏移重算（禁 += 累加）、槽背景按容器真实坐标画。
 */
public class GuiDropRecipes extends GuiContainerNPCInterface2<ContainerDropRecipes>
        implements ICustomScrollListener {

    private static final ResourceLocation SLOT_TEX =
            new ResourceLocation("customnpcs", "textures/gui/slot.png");
    /** arrow 透明裁片（231b 从 anvil.png 导出，直接复用）。 */
    private static final ResourceLocation ARROW_TEX =
            new ResourceLocation("cnpcplus", "textures/gui/anvil_arrow.png");

    private GuiCustomScrollNop scroll;
    private final ContainerDropRecipes container;
    private final List<DropRecipeData> recipes = new ArrayList<>();
    private String selectedName = null;
    private int selectedId = -1;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cnpcplus");

    private static final int BTN_NEW = 3;
    private static final int BTN_REMOVE = 4;
    private static final int BTN_SAVE = 2;
    /** 233a 仅名字检查开关（合成槽左边）。 */
    private static final int BTN_NAMEONLY = 5;
    /** 233c 音效选择按钮（复用 CNPC GuiSoundSelection 子界面）。 */
    private static final int BTN_SOUND = 6;
    /** 233b 绑定当前维度+坐标 / 清除绑定。 */
    private static final int BTN_BIND = 8;
    private static final int BTN_UNBIND = 9;

    /** 「新建草稿」状态：点过「新建」但还没保存（沿用熔炼/锻造机制，防表单死锁）。 */
    private boolean draft = false;

    /** 232 本轮 GUI 不放开关，固定 false 落盘；data 字段已留位（用户后续要改再加控件）。 */
    private boolean nameOnly = false;

    /** 233c 音效 id（空=不播；保存随主 NBT 落盘）。 */
    private String soundId = "";

    /** 233b 生效绑定（内存编辑态：loadRecipe 载入、绑定按钮改、保存写回）。 */
    private String bindDim = "";
    private boolean hasBindPos = false;
    private int bindX, bindY, bindZ;

    /** 233d 粒子特效编辑态（深拷贝；SubGuiDropFx 确定写回、保存按钮落盘）。 */
    private final java.util.List<top.cnpcplus.drop.fx.DropFxConfig> fxFinal = new java.util.ArrayList<>();

    /** 233d 粒子编辑器按钮。 */
    private static final int BTN_FX = 7;

    /** SubGuiDropFx 确定回调：写回编辑态（关闭链随后 initGui 刷新）。 */
    public void applyFxCopy(java.util.List<top.cnpcplus.drop.fx.DropFxConfig> result) {
        this.fxFinal.clear();
        for (top.cnpcplus.drop.fx.DropFxConfig c : result) this.fxFinal.add(c.copy());
    }

    public GuiDropRecipes(ContainerDropRecipes container, Inventory inv, Component titleIn) {
        super(NoppesUtil.getLastNpc(), container, inv, titleIn);
        this.container = container;
        this.drawDefaultBackground = false;
        this.setBackground("inventorymenu.png");
        this.imageWidth = 420;
        this.imageHeight = 250;
        this.title = "";
        this.menuYOffset = 0;
        // 进入铁律（用户「进入不许任何理由的全量重建/顿一下」）：缓存非空 = 直接本地渲染，一个包都不发；
        // 只在首次（缓存为空）向服务端要一次全量列表，收到后 Sync 会回调 refreshFromServer。
        boolean cacheEmpty = DropClientData.get().isEmpty();
        if (cacheEmpty) {
            DropPacketHandler.CHANNEL.sendToServer(new PacketDropRequestList());
        }
    }

    @Override
    public void m_7856_() {
        super.m_7856_();
        // 界面整体偏移：guiLeft/guiTop 与 vanilla leftPos/topPos 一起按居中基准+偏移重算，
        // 不用 += 累加（m_7856_ 被二次调用时会叠加跑偏——熔炼版已实证）。
        this.guiLeft = (this.width - this.imageWidth) / 2 + DropLayout.guiOffsetX();
        this.guiTop = (this.height - this.imageHeight) / 2 + DropLayout.guiOffsetY();
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

        // 配方名称输入框（顶部最左，宽158，始终显示）
        this.addTextField(new GuiTextFieldNop(30, this,
                this.guiLeft + DropLayout.nameX(), this.guiTop + DropLayout.nameY(),
                DropLayout.nameWidth(), 20, this.nameText()));

        // 233a 仅名字检查开关：合成槽左边（2×2 starts x36 前的空隙），外观照抄铁砧/熔炼。
        this.addButton(new GuiIconToggleButton(this, BTN_NAMEONLY,
                this.guiLeft + DropLayout.switchX(), this.guiTop + DropLayout.switchY(),
                DropLayout.switchW(), DropLayout.switchH(),
                new ItemStack(Items.NAME_TAG), this.nameOnly,
                Component.translatable("cnpcplus.nameonly")));

        // 右侧按钮列（新建/移除/保存/音效/绑定/清除绑定，始终显示）
        int bx = this.guiLeft + DropLayout.btnX();
        int by = this.guiTop + DropLayout.btnY();
        int bs = DropLayout.btnSpacing();
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_NEW, bx, by, 84, 20, "gui.add"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_REMOVE, bx, by + bs, 84, 20, "gui.remove"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_SAVE, bx, by + bs * 2, 84, 20, "gui.save"));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_SOUND, bx, by + bs * 3, 84, 20,
                Component.translatable("cnpcplus.drop.sound").getString()));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_BIND, bx, by + bs * 4, 84, 20,
                Component.translatable("cnpcplus.drop.bind").getString()));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_UNBIND, bx, by + bs * 5, 84, 20,
                Component.translatable("cnpcplus.drop.unbind").getString()));
        this.addButton(new GuiButtonNop((IGuiInterface) this, BTN_FX, bx, by + bs * 6, 84, 20,
                Component.translatable("cnpcplus.dropfx.open").getString()));

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
                    this.guiLeft + DropLayout.btnX(),
                    this.guiTop + DropLayout.btnY() + DropLayout.btnSpacing() * 3 + 6,
                    0xFFFF5555, false);
        }
        // 233b 绑定信息小字（按钮列下方）：显示当前配方绑定的维度+坐标，空=不限
        String bindText = this.bindDim.isEmpty()
                ? Component.translatable("cnpcplus.drop.unbound").getString()
                : this.bindDim + (this.hasBindPos
                    ? " " + this.bindX + "," + this.bindY + "," + this.bindZ : "");
        graphics.drawString(this.font, bindText,
                this.guiLeft + DropLayout.btnX(),
                this.guiTop + DropLayout.btnY() + DropLayout.btnSpacing() * 7 + 6,
                0xFFAAAAAA, false);
    }

    /**
     * 顶部菜单条位置由基类用 guiTop 定死，改了 guiTop 后必须按新值重排（反射调一次 initGui，与铁砧版相同）。
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
                    .error("掉落物界面顶部菜单重定位失败（界面整体偏移将不影响菜单条）", e);
        }
    }

    private String nameText() {
        // 草稿态显示草稿名（228「点添加无反应」同款修复：nameText 只看 selectedId 会覆盖草稿名）
        if (this.draft && this.selectedName != null) return this.selectedName;
        DropRecipeData d = DropClientData.byId(this.selectedId);
        return d == null ? "" : (d.name == null ? "" : d.name);
    }

    private void refreshScroll() {
        this.recipes.clear();
        this.recipes.addAll(DropClientData.get());
        List<String> names = new ArrayList<>();
        for (DropRecipeData d : this.recipes) names.add(d.name);
        this.scroll.setList(names);
        if (this.selectedName != null) {
            this.scroll.setSelected(this.selectedName);
        } else if (!names.isEmpty()) {
            this.scroll.setSelected(names.get(0));
            this.selectedName = names.get(0);
            this.loadRecipe(0);
            // 首开自动选中第一条：同时让服务端把该配方填进五槽（新容器此时必为空槽，
            // 填入即「列表选中项=槽内展示」状态一致；服务端会先归还槽内原有物品，不会销毁）。
            if (this.selectedId >= 0) {
                DropPacketHandler.CHANNEL.sendToServer(new PacketDropSelect(this.selectedId));
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
        DropRecipeData d = this.recipes.get(index);
        this.selectedId = d.id;
        this.selectedName = d.name;
        this.nameOnly = d.nameOnly;
        this.soundId = d.soundId == null ? "" : d.soundId;
        this.bindDim = d.dim == null ? "" : d.dim;
        this.hasBindPos = d.hasBindPos;
        this.bindX = d.bindX;
        this.bindY = d.bindY;
        this.bindZ = d.bindZ;
        this.fxFinal.clear();
        for (top.cnpcplus.drop.fx.DropFxConfig c : d.fxs) this.fxFinal.add(c.copy());
        // 选中列表里的正式配方 = 草稿结束（否则保存后 refreshFromServer 仍按草稿名匹配）
        this.draft = false;
        if (this.getTextField(30) != null) this.getTextField(30).setValue(d.name == null ? "" : d.name);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == BTN_NEW) {
            // 「添加配方」= 清空表单 + 清空五个槽 + 进入草稿 + 自动生成随机名字。
            // 清槽由服务端权威执行（PacketDropSelect(-1) 查不到配方时把五槽设为 EMPTY 并归还物品），
            // 客户端不自己 setItem，避免与点击包的 stateId 对不上导致物品显示错乱。
            DropPacketHandler.CHANNEL.sendToServer(new PacketDropSelect(-1));
            this.selectedId = -1;
            this.draft = true;
            this.nameOnly = false;
            this.soundId = "";
            this.bindDim = "";
            this.hasBindPos = false;
            this.fxFinal.clear();
            this.selectedName = "new" + System.currentTimeMillis() % 100000;
            if (this.getTextField(30) != null) this.getTextField(30).setValue(this.selectedName);
            this.m_7856_();
        } else if (button.id == BTN_REMOVE) {
            if (this.selectedId >= 0) {
                DropPacketHandler.CHANNEL.sendToServer(new PacketDropRemove(this.selectedId));
                this.selectedId = -1;
                this.draft = false;
                this.nameOnly = false;
                this.selectedName = null;
                // 服务端删除后会主动推 Sync（列表都由它收口），无需再手动 RequestList。
            }
        } else if (button.id == BTN_SAVE) {
            this.saveRecipe();
        } else if (button.id == BTN_NAMEONLY) {
            // 233a 开关翻转：GuiIconToggleButton 的显示态在 m_7856_ 里按字段重建
            this.nameOnly = !this.nameOnly;
            this.m_7856_();
        } else if (button.id == BTN_SOUND) {
            // 233c：复用 CNPC 音效选择子界面；结果在 subGuiClosed 里读 selectedResource
            this.setSubGui(new GuiSoundSelection(this.soundId == null ? "" : this.soundId));
        } else if (button.id == BTN_BIND) {
            // 233b：绑定玩家当前所在维度+脚下方块坐标（记入内存，保存才落盘）
            if (this.minecraft != null && this.minecraft.player != null) {
                this.bindDim = this.minecraft.player.level().dimension().location().toString();
                var pos = this.minecraft.player.blockPosition();
                this.bindX = pos.getX();
                this.bindY = pos.getY();
                this.bindZ = pos.getZ();
                this.hasBindPos = true;
                this.m_7856_();
            }
        } else if (button.id == BTN_UNBIND) {
            // 233b：清除绑定=恢复不限维度不限坐标（兼容 232 存量语义）
            this.bindDim = "";
            this.hasBindPos = false;
            this.m_7856_();
        } else if (button.id == BTN_FX) {
            // 233d：打开粒子特效编辑器（编辑副本；确定写内存、保存按钮落盘）
            this.setSubGui(new top.cnpcplus.drop.fx.client.SubGuiDropFx(this, this.fxFinal));
        }
    }

    private void saveRecipe() {
        // 客户端先自检（与服务端 PacketDropSave 同规则）：至少两个输入槽有物 + 结果必放。
        // 不满足时服务端会静默 return（与铁砧 need 同款坑），这里直接给出可见提示。
        int filled = 0;
        for (int i = 0; i < 4; i++) {
            if (!this.container.getInput(i).isEmpty()) filled++;
        }
        if (filled < 2 || this.container.getOutput().isEmpty()) {
            this.notice = Component.translatable("cnpcplus.drop.need").getString();
            LOGGER.warn("[drop] 保存被客户端预检拦截: filled={} r={} (规则=输入≥2槽+结果必放)",
                    filled, !this.container.getOutput().isEmpty());
            return;
        }
        this.notice = null;
        DropRecipeData d = new DropRecipeData();
        d.id = this.selectedId;
        d.nameOnly = this.nameOnly;
        d.soundId = this.soundId == null ? "" : this.soundId;
        d.dim = this.bindDim == null ? "" : this.bindDim;
        d.hasBindPos = this.hasBindPos;
        d.bindX = this.bindX;
        d.bindY = this.bindY;
        d.bindZ = this.bindZ;
        d.fxs.clear();
        for (top.cnpcplus.drop.fx.DropFxConfig c : this.fxFinal) d.fxs.add(c.copy());
        String name = this.getTextField(30) != null ? this.getTextField(30).getValue() : "";
        d.name = (name == null || name.trim().isEmpty()) ? "new" : name.trim();
        if (d.name.equals("new") && d.id < 0) {
            d.name = "new" + System.currentTimeMillis() % 10000;
        }
        // 五槽物品刻意不带进包里：服务端只认它自己容器里的 ItemStack（见 PacketDropSave），
        // 客户端发过去的会被直接丢弃（C→S 32767 上限，潜影盒大 NBT 会撞线）。
        this.selectedName = d.name;
        DropPacketHandler.CHANNEL.sendToServer(new PacketDropSave(d.toNBT(false)));
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
            DropPacketHandler.CHANNEL.sendToServer(new PacketDropSelect(this.selectedId));
        }
        this.m_7856_();
    }

    /**
     * 233c：音效选择子界面关闭回调（MixinGuiNpcBard:126-147 同款模式）。
     * GuiSoundSelection.selectedResource=null 表示未选关闭，保持原值。
     */
    @Override
    public void subGuiClosed(net.minecraft.client.gui.screens.Screen subgui) {
        super.subGuiClosed(subgui);
        if (subgui instanceof GuiSoundSelection gss && gss.selectedResource != null) {
            this.soundId = gss.selectedResource.toString();
            this.m_7856_();
        }
    }

    /**
     * 收到服务端配方列表同步后刷新（DropClientSync.accept 调用）。
     * 保存成功会触发同步下发，此时草稿已经变成正式配方，按名字把它选中，草稿状态收尾。
     */
    public void refreshFromServer() {
        if (this.draft && this.selectedName != null) {
            for (int i = 0; i < DropClientData.get().size(); i++) {
                DropRecipeData d = DropClientData.get().get(i);
                if (this.selectedName.equals(d.name)) {
                    // 231b bug4 同款修复：按服务端落盘后的真值整体回填，只回填 selectedId 会丢状态。
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

        // 五个槽位背景：直接读容器实际槽位真实坐标（-1 让 18x18 框套住 16x16 槽位）
        drawSlotBgAt(graphics, ContainerDropRecipes.SLOT_INDEX_INPUT_0);
        drawSlotBgAt(graphics, ContainerDropRecipes.SLOT_INDEX_INPUT_1);
        drawSlotBgAt(graphics, ContainerDropRecipes.SLOT_INDEX_INPUT_2);
        drawSlotBgAt(graphics, ContainerDropRecipes.SLOT_INDEX_INPUT_3);
        drawSlotBgAt(graphics, ContainerDropRecipes.SLOT_INDEX_OUTPUT);
        // 箭头（复用 anvil_arrow.png 透明裁片；9 参 blit 带 texW/H——6 参版 256² 假设小图不显示）
        graphics.blit(ARROW_TEX,
                this.guiLeft + DropLayout.arrowX(), this.guiTop + DropLayout.arrowY(),
                0f, 0f, 22, 15, 22, 15);
    }

    /** 按容器里实际槽位的真实坐标画 18x18 背景框。 */
    private void drawSlotBgAt(GuiGraphics graphics, int slotIndex) {
        int sx = this.container.slotRenderX(slotIndex);
        int sy = this.container.slotRenderY(slotIndex);
        graphics.blit(SLOT_TEX, this.leftPos + sx - 1, this.topPos + sy - 1, 0, 0, 18, 18);
    }
}
