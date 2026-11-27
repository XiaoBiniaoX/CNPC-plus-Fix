package bin.cnpcplus.quest;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Published after a player obtains a produced item or a drop recipe succeeds. */
public final class ItemProductionEvent extends Event {
    private final EntityPlayer player;
    private final ItemStack stack;
    private final CraftStation station;
    private final World world;
    private final BlockPos pos;
    private final int count;
    private final String recipeId;

    public ItemProductionEvent(EntityPlayer player, ItemStack stack, CraftStation station,
                               World world, BlockPos pos, int count, String recipeId) {
        this.player = player;
        this.stack = stack.copy();
        this.station = station;
        this.world = world;
        this.pos = pos.toImmutable();
        this.count = Math.max(0, count);
        this.recipeId = recipeId == null ? "" : recipeId;
    }

    public EntityPlayer getPlayer() { return player; }
    public ItemStack getStack() { return stack; }
    public CraftStation getStation() { return station; }
    public World getWorld() { return world; }
    public BlockPos getPos() { return pos; }
    public int getCount() { return count; }
    public String getRecipeId() { return recipeId; }
}
