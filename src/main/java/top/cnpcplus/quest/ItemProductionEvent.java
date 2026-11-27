package top.cnpcplus.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

/** Fired only after a server-side player takes an output or a drop recipe creates its result. */
public final class ItemProductionEvent extends Event {
    public enum Source { WORKBENCH, CARPENTRY, ANVIL, SMITHING, BREWING, CAMPFIRE, FURNACE, DROP, GRINDSTONE }
    private final Player player;
    private final ItemStack stack;
    private final Source source;
    private final Level level;
    private final BlockPos pos;
    private final int count;
    private final String station;
    private final String recipeId;

    public ItemProductionEvent(Player player, ItemStack stack, Source source, Level level, BlockPos pos, int count) {
        this(player, stack, source, level, pos, count, "", "");
    }

    public ItemProductionEvent(Player player, ItemStack stack, Source source, Level level, BlockPos pos, int count,
                               String station, String recipeId) {
        this.player = player;
        this.stack = stack.copy();
        this.source = source;
        this.level = level;
        this.pos = pos.immutable();
        this.count = Math.max(0, count);
        this.station = station == null ? "" : station;
        this.recipeId = recipeId == null ? "" : recipeId;
    }
    public Player getPlayer() { return player; }
    public ItemStack getStack() { return stack; }
    public Source getSource() { return source; }
    public Level getLevel() { return level; }
    public BlockPos getPos() { return pos; }
    public int getCount() { return count; }
    public String getStation() { return station; }
    public String getRecipeId() { return recipeId; }
}
