package bin.cnpcplus.crafting;

import bin.cnpcplus.common.IAnvilRecipeAccess;
import net.minecraft.block.BlockAnvil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;

/** 自定义配方支持零/负等级费用；普通铁砧逻辑仍委托原结果槽。 */
public final class AnvilResultSlot extends Slot {
    private final ContainerRepair container;
    private final Slot vanilla;
    private final World world;
    private final BlockPos pos;
    private ItemStack shifted = ItemStack.EMPTY;
    public AnvilResultSlot(ContainerRepair container, Slot vanilla, World world, BlockPos pos) {
        super(vanilla.inventory, vanilla.getSlotIndex(), vanilla.xPos, vanilla.yPos);
        this.container = container; this.vanilla = vanilla; this.world = world; this.pos = pos;
        this.slotNumber = vanilla.slotNumber;
    }
    @Override public boolean isItemValid(ItemStack stack) { return false; }
    @Override public void onSlotChange(ItemStack remaining, ItemStack original) {
        shifted = original.copy();
        shifted.setCount(original.getCount() - remaining.getCount());
        super.onSlotChange(remaining, original);
    }
    @Override public boolean canTakeStack(EntityPlayer player) {
        IAnvilRecipeAccess access = (IAnvilRecipeAccess) container;
        if (!access.cnpcplus$isCustomRecipe()) return vanilla.canTakeStack(player);
        if (!getHasStack()) return false;
        CustomRecipe recipe = access.cnpcplus$customRecipe();
        if (!world.isRemote && (recipe == null || !bin.cnpcplus.craftingview.RecipeAvailability.allows(recipe.availability, player))) return false;
        if (recipe != null && CustomRecipe.matchSlots(new ItemStack[]{container.getSlot(0).getStack(), container.getSlot(1).getStack()},
                recipe.inputs, recipe.nameOnly, recipe.shapeless, false) == null) return false;
        return player.capabilities.isCreativeMode || container.maximumCost <= player.experienceLevel;
    }
    @Override public ItemStack onTake(EntityPlayer player, ItemStack stack) {
        ItemStack produced = shifted.isEmpty() ? stack.copy() : shifted;
        shifted = ItemStack.EMPTY;
        IAnvilRecipeAccess access = (IAnvilRecipeAccess) container;
        if (!access.cnpcplus$isCustomRecipe()) {
            ItemStack result = vanilla.onTake(player, stack);
            bin.cnpcplus.quest.ItemProductionEvents.post(player, produced,
                    bin.cnpcplus.quest.CraftStation.ANVIL, pos, produced.getCount(), "vanilla");
            pushSlots(player);
            return result;
        }
        if (world.isRemote) return stack;
        CustomRecipe recipe = access.cnpcplus$customRecipe();
        ItemStack left = container.getSlot(0).getStack().copy(), right = container.getSlot(1).getStack().copy();
        int[] slots = CustomRecipe.matchSlots(new ItemStack[]{left, right}, recipe.inputs, recipe.nameOnly, recipe.shapeless, false);
        if (slots == null) return stack;
        int leftCost = recipe.inputs[slots[0]].getCount(), rightCost = recipe.inputs[slots[1]].getCount();
        int cost = container.maximumCost;
        float chance = ForgeHooks.onAnvilRepair(player, stack, left, right);
        access.cnpcplus$setTakingRecipe(true);
        try {
            if (!player.capabilities.isCreativeMode) player.addExperienceLevel(-cost);
            left.shrink(leftCost); right.shrink(rightCost);
            container.getSlot(0).putStack(left.getCount() <= 0 ? ItemStack.EMPTY : left);
            container.getSlot(1).putStack(right.getCount() <= 0 ? ItemStack.EMPTY : right);
            putStack(ItemStack.EMPTY);
        } finally { access.cnpcplus$setTakingRecipe(false); }
        IBlockState state = world.getBlockState(pos);
        if (!player.capabilities.isCreativeMode && state.getBlock() == Blocks.ANVIL && player.getRNG().nextFloat() < chance) {
            int damage = state.getValue(BlockAnvil.DAMAGE) + 1;
            if (damage > 2) { world.setBlockToAir(pos); world.playEvent(1029, pos, 0); }
            else { world.setBlockState(pos, state.withProperty(BlockAnvil.DAMAGE, damage), 2); world.playEvent(1030, pos, 0); }
        } else world.playEvent(1030, pos, 0);
        container.updateRepairOutput();
        container.detectAndSendChanges();
        pushSlots(player);
        bin.cnpcplus.quest.ItemProductionEvents.post(player, produced,
                bin.cnpcplus.quest.CraftStation.ANVIL, pos, produced.getCount(), Integer.toString(recipe.id));
        return stack;
    }

    /**
     * 铁砧也一样：取走结果后 updateRepairOutput 立刻又算出同一个结果，
     * detectAndSendChanges 的比较缓存还是那个值，差值包不会发；客户端已经清空结果槽，
     * 于是槽位空着却还能点出东西。这里把 0/1/2 三个槽位显式补发给客户端。
     */
    private void pushSlots(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        for (int i = 0; i < 3; i++) {
            mp.connection.sendPacket(new SPacketSetSlot(container.windowId, i,
                    container.getSlot(i).getStack().copy()));
        }
    }
}
