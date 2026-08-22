package top.cnpcplus.smelting;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 自定义熔炼配方编辑容器：3 个配方槽（0=燃料 1=被熔炼物 2=熔炼物）+ 玩家背包。
 * 这三个槽只是「用来摆样子定义配方」的，不是真实存储；坐标见 SmeltingLayout。
 * 界面上的火焰/箭头只是循环播放的动画，由 GUI 自己按客户端 tick 画，容器不需要同步燃烧进度。
 */
public class ContainerSmeltingRecipes extends AbstractContainerMenu {

    public final SimpleContainer recipe = new SimpleContainer(3);
    public int selectedId = -1;
    private static final int FUEL = 0, INPUT = 1, OUTPUT = 2;

    public ContainerSmeltingRecipes(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData == null ? -1 : extraData.readInt());
    }

    public ContainerSmeltingRecipes(int containerId, Inventory playerInventory, int selectedId) {
        super(SmeltingMenus.SMELTING_RECIPES.get(), containerId);
        this.selectedId = selectedId;
        // 本构造在两端都会执行（客户端由 IForgeMenuType 从 buf 构造）。
        // 槽位内容以服务端为准：SmeltingRecipeRegistry 在客户端返回 null，客户端槽位由
        // 服务端 broadcastChanges 同步过来，避免客户端读本地配方文件造成显示不一致。
        SmeltingRecipeData d = SmeltingRecipeRegistry.get(selectedId);
        if (d != null) {
            this.recipe.setItem(FUEL, d.fuel.copy());
            this.recipe.setItem(INPUT, d.input.copy());
            this.recipe.setItem(OUTPUT, d.output.copy());
        }
        // 三个配方槽：只用布局坐标本身，不加界面整体偏移。
        // 界面整体偏移由 GUI 在 init 里同步下移 guiTop 与 vanilla topPos 实现（原版渲染槽位用 topPos + slot.y），
        // 若这里再加一次就会变成双倍偏移。
        this.addSlot(new Slot(this.recipe, INPUT, SmeltingLayout.slotInputX(), SmeltingLayout.slotInputY()));
        this.addSlot(new Slot(this.recipe, FUEL, SmeltingLayout.slotFuelX(), SmeltingLayout.slotFuelY()));
        this.addSlot(new Slot(this.recipe, OUTPUT, SmeltingLayout.slotOutputX(), SmeltingLayout.slotOutputY()));
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
     * 禁用 shift+左键快速移动：三个配方槽是「配方定义」而不是真实存储，
     * 让原版快速移动逻辑介入会在两个 SimpleContainer 之间搬运物品，产生复制/丢失。
     * 返回 EMPTY 而非 null（返回 null 会让原版 clicked() 里的 isEmpty() 抛 NPE，任务槽位曾踩过）。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** 编辑界面不绑定方块/实体，没有「玩家走远了要自动关」的概念，恒定有效。 */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * 关界面时把三个配方槽里的东西还给玩家。
     * 这三个槽只是用来「摆样子定义配方」的，玩家从背包拖进来的是真实物品，
     * 不还回去就会被静默销毁（配方本身已经在保存时写进 Registry，不依赖这几个槽）。
     */
    @Override
    public void removed(Player player) {
        super.removed(player);
        // 只在服务端还物品。removed 两端都会跑，客户端那次 clearContainer 会走
        // Inventory.placeItemBackInInventory → 在客户端也生成一份，于是「保存后点 X」
        // 会凭空多出一份物品（用户实测复现）。服务端才是权威。
        if (player.level().isClientSide()) return;
        this.clearContainer(player, this.recipe);
    }

    public ItemStack getFuel() { return this.recipe.getItem(FUEL); }
    public ItemStack getInput() { return this.recipe.getItem(INPUT); }
    public ItemStack getOutput() { return this.recipe.getItem(OUTPUT); }

    /** 配方槽在容器里的槽位序号（0=被熔炼物 1=燃料 2=熔炼物），供 GUI 取「实际槽位」真实坐标画背景用。 */
    public static final int SLOT_INDEX_INPUT = 0;
    public static final int SLOT_INDEX_FUEL = 1;
    public static final int SLOT_INDEX_OUTPUT = 2;

    /** 取实际槽位的真实渲染 X（原版用 leftPos + slot.x 渲染物品，背景照这个画就永不错位）。 */
    public int slotRenderX(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size() ? this.slots.get(slotIndex).x : 0;
    }

    /** 取实际槽位的真实渲染 Y。 */
    public int slotRenderY(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.slots.size() ? this.slots.get(slotIndex).y : 0;
    }
}