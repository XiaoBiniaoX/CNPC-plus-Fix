package top.cnpcplus.drop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义掉落物配方编辑容器（阶段232 从 ContainerAnvilRecipes 复制改五槽）：
 * 5 个配方槽（0-3=2×2 输入 4=结果）+ 玩家背包。
 * 这些槽只是「用来摆样子定义配方」的，不是真实存储；坐标见 DropLayout。
 * slotPreview 吞/归还机制照抄锻造 228h（预览副本非玩家资产，归还=白送）。
 */
public class ContainerDropRecipes extends AbstractContainerMenu {

    public final SimpleContainer recipe = new SimpleContainer(5);
    public int selectedId = -1;

    /** 2×2 四个输入槽在容器里的序号。 */
    public static final int SLOT_INDEX_INPUT_0 = 0;
    public static final int SLOT_INDEX_INPUT_1 = 1;
    public static final int SLOT_INDEX_INPUT_2 = 2;
    public static final int SLOT_INDEX_INPUT_3 = 3;
    /** 结果槽。 */
    public static final int SLOT_INDEX_OUTPUT = 4;

    /**
     * 每槽「预览副本」标记（228 复制修复同款）：PacketDropSelect 回填的 stack 是
     * Registry 数据的副本（凭空产生、不是玩家资产）。规则：回填 → true；
     * 玩家对该槽任何 clicked 操作 → false（内容可能是真物品，覆盖时必须归还防销毁）；
     * 覆盖/关面板时 true 槽静默吞、false 槽归还。
     */
    public final boolean[] slotPreview = new boolean[5];

    public ContainerDropRecipes(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData == null ? -1 : extraData.readInt());
    }

    public ContainerDropRecipes(int containerId, Inventory playerInventory, int selectedId) {
        super(DropMenus.TYPE, containerId);
        this.selectedId = selectedId;
        // 槽位填充不在构造里做：由 PacketDropSelect 服务端权威填入（打开时=空槽，
        // GUI 首开自动选中后发 Select；构造填会绕开「先归还槽内物品」的防销毁逻辑）。
        // 2×2 紧凑（DropLayout）：上排 0/1、下排 2/3，右侧箭头+结果。
        this.addSlot(new Slot(this.recipe, SLOT_INDEX_INPUT_0, DropLayout.slot0X(), DropLayout.slot0Y()));
        this.addSlot(new Slot(this.recipe, SLOT_INDEX_INPUT_1, DropLayout.slot1X(), DropLayout.slot1Y()));
        this.addSlot(new Slot(this.recipe, SLOT_INDEX_INPUT_2, DropLayout.slot2X(), DropLayout.slot2Y()));
        this.addSlot(new Slot(this.recipe, SLOT_INDEX_INPUT_3, DropLayout.slot3X(), DropLayout.slot3Y()));
        this.addSlot(new Slot(this.recipe, SLOT_INDEX_OUTPUT, DropLayout.slotResultX(), DropLayout.slotResultY()));
        // 玩家背包：标准位（8,113 / 8,171），与锻造/铁砧同 imageHeight 250 对齐
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
     * 内容可能是玩家真物品，之后覆盖必须归还防销毁（228 复制修复同款钩子）。
     */
    @Override
    public void clicked(int slotId, int button,
                        net.minecraft.world.inventory.ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        if (slotId >= 0 && slotId < 5) {
            this.slotPreview[slotId] = false;
        }
    }

    /** 编辑界面不绑定方块/实体，恒定有效。 */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 关界面处理（228 复制修复同款）：只退还玩家真物品；槽内预览副本静默吞掉。
     * 只在服务端执行（客户端那次会凭空复制物品——熔炼实证）。
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;
        for (int i = 0; i < 5; i++) {
            ItemStack old = this.recipe.getItem(i);
            if (old.isEmpty()) continue;
            this.recipe.setItem(i, ItemStack.EMPTY);
            if (this.slotPreview[i]) continue; // 预览副本：非玩家资产，吞掉
            if (!player.getInventory().add(old)) player.drop(old, false);
        }
    }

    public ItemStack getInput(int i) { return this.recipe.getItem(i); }
    public ItemStack getOutput() { return this.recipe.getItem(SLOT_INDEX_OUTPUT); }

    /** 取实际槽位的真实渲染 X（原版用 leftPos + slot.x 渲染物品，背景照这个画就永不错位）。 */
    public int slotRenderX(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size() ? this.slots.get(slotIndex).x : 0;
    }

    /** 取实际槽位的真实渲染 Y。 */
    public int slotRenderY(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size() ? this.slots.get(slotIndex).y : 0;
    }
}
