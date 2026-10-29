package top.cnpcplus.smithing;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义锻造配方编辑容器（226 从 ContainerSmeltingRecipes 复制，槽位换锻造台）：
 * 4 个配方槽（0=模板 1=基座 2=升级材料 3=结果）+ 玩家背包。
 * 这四个槽只是「用来摆样子定义配方」的，不是真实存储；坐标见 SmithingLayout。
 * 226 本步：选中配方→填槽的数据链路后续阶段接入（构造里不读任何配方存储）。
 */
public class ContainerSmithingRecipes extends AbstractContainerMenu {

    public final SimpleContainer recipe = new SimpleContainer(4);
    public int selectedId = -1;
    private static final int TEMPLATE = 0, BASE = 1, ADDITION = 2, OUTPUT = 3;

    /**
     * 每槽「预览副本」标记（228 修物品复制）：PacketSmithingSelect 回填的 4 个 stack 是
     * Registry 数据的副本（凭空产生、不是玩家资产）。若归还/退还时把它们当玩家物品还进背包，
     * 每点一次条目/每关一次面板就白送 4 样 = 无限复制。
     * 规则：回填 → 对应槽 true；玩家对该槽任何菜单操作（clicked 覆写）→ 该槽 false（内容改为
     * 「可能是真物品」，必须走归还路径防销毁）；覆盖/关面板时 true 槽静默吞、false 槽归还。
     * 玩家亲手从槽里拖出预览副本拿走 = 已知边界（根除需 ghost 渲染改造，见 findings）。
     */
    public final boolean[] slotPreview = new boolean[4];

    public ContainerSmithingRecipes(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData == null ? -1 : extraData.readInt());
    }

    public ContainerSmithingRecipes(int containerId, Inventory playerInventory, int selectedId) {
        super(SmithingMenus.TYPE, containerId);
        this.selectedId = selectedId;
        // 槽位填充不在构造里做：由 PacketSmithingSelect 服务端权威填入（打开时=空槽，
        // GUI 首开自动选中后发 Select；构造填会绕开「先归还槽内物品」的防销毁逻辑）。
        // 槽位内容以服务端为准（与熔炼同一约定）。
        // 四个配方槽：只用布局坐标本身，不加界面整体偏移（偏移由 GUI 同步 guiLeft/topPos 实现）。
        this.addSlot(new Slot(this.recipe, TEMPLATE, SmithingLayout.slotTemplateX(), SmithingLayout.slotTemplateY()));
        this.addSlot(new Slot(this.recipe, BASE, SmithingLayout.slotBaseX(), SmithingLayout.slotBaseY()));
        this.addSlot(new Slot(this.recipe, ADDITION, SmithingLayout.slotAdditionX(), SmithingLayout.slotAdditionY()));
        this.addSlot(new Slot(this.recipe, OUTPUT, SmithingLayout.slotResultX(), SmithingLayout.slotResultY()));
        // 玩家背包：标准位（8,113 / 8,171）
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 113 + i * 18));
            }
        }
        for (int j = 0; j < 9; j++) {
            this.addSlot(new Slot(playerInventory, j, 8 + j * 18, 171));
        }
    }

    /**
     * 禁用 shift+左键快速移动：配方槽是「配方定义」而非真实存储，放行会产生物品复制/丢失。
     * 返回 EMPTY 而非 null（null 会让原版 clicked() 的 isEmpty() 抛 NPE）。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * 玩家碰过配方槽（拿起/放下/合堆，任何点击类型）→ 该槽不再是纯预览：
     * 内容可能是玩家真物品，之后覆盖必须归还防销毁（228 复制修复的失效钩子）。
     * 点背包/窗外等非配方槽不清（预览态保持，连点条目走吞路径）。
     */
    @Override
    public void clicked(int slotId, int button,
                        net.minecraft.world.inventory.ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        if (slotId >= 0 && slotId < 4) {
            this.slotPreview[slotId] = false;
        }
    }

    /** 编辑界面不绑定方块/实体，恒定有效。 */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 关界面处理（228 复制修复）：只退还玩家真物品；槽内预览副本静默吞掉——
     * 原 clearContainer 全量退还会把回填副本也还进背包 = 关一次面板白送 4 样。
     * 只在服务端执行（客户端那次会凭空复制物品——熔炼实证）。
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;
        for (int i = 0; i < 4; i++) {
            ItemStack old = this.recipe.getItem(i);
            if (old.isEmpty()) continue;
            this.recipe.setItem(i, ItemStack.EMPTY);
            if (this.slotPreview[i]) continue; // 预览副本：非玩家资产，吞掉
            if (!player.getInventory().add(old)) player.drop(old, false);
        }
    }

    public ItemStack getTemplate() { return this.recipe.getItem(TEMPLATE); }
    public ItemStack getBase() { return this.recipe.getItem(BASE); }
    public ItemStack getAddition() { return this.recipe.getItem(ADDITION); }
    public ItemStack getOutput() { return this.recipe.getItem(OUTPUT); }

    /** 配方槽在菜单里的槽位序号（0=模板 1=基座 2=升级材料 3=结果），供 GUI 画 18x18 背景用。 */
    public static final int SLOT_INDEX_TEMPLATE = 0;
    public static final int SLOT_INDEX_BASE = 1;
    public static final int SLOT_INDEX_ADDITION = 2;
    public static final int SLOT_INDEX_OUTPUT = 3;

    /** 取实际槽位的真实渲染 X（原版用 leftPos + slot.x 渲染物品，背景照这个画就永不错位）。 */
    public int slotRenderX(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size() ? this.slots.get(slotIndex).x : 0;
    }

    /** 取实际槽位的真实渲染 Y。 */
    public int slotRenderY(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size() ? this.slots.get(slotIndex).y : 0;
    }
}
