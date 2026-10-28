package bin.cnpcplus.crafting;

import bin.cnpcplus.fx.FxEngine;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketCustomSound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/** 玩家丢物驱动；重启前的旧掉落物需重丢。只扫描落地点，不遍历世界实体。 */
public final class DropCrafting {
    private static final Map<WorldServer, Deque<Pending>> PENDING = new IdentityHashMap<>();
    private static final Set<EntityItem> ELIGIBLE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Pending {
        final EntityItem item;
        final long expires;
        Pending(EntityItem item, long expires) { this.item = item; this.expires = expires; }
    }
    private DropCrafting() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void toss(ItemTossEvent event) {
        EntityItem item = event.getEntityItem();
        if (!(item.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) item.world;
        ELIGIBLE.add(item);
        PENDING.computeIfAbsent(world, ignored -> new ArrayDeque<>()).addLast(new Pending(item, world.getTotalWorldTime() + 200));
    }

    @SubscribeEvent
    public static void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.world instanceof WorldServer)) return;
        WorldServer world = (WorldServer) event.world;
        Deque<Pending> queue = PENDING.get(world);
        if (queue == null) return;
        Set<BlockPos> visited = new HashSet<>();
        for (int budget = Math.min(32, queue.size()); budget > 0; budget--) {
            Pending pending = queue.removeFirst();
            if (pending.item.isDead || world.getTotalWorldTime() > pending.expires) continue;
            boolean crafted = false;
            if (pending.item.onGround && visited.add(pending.item.getPosition())) crafted = craft(world, pending.item);
            if (!crafted && !pending.item.isDead) queue.addLast(pending);
        }
        if (queue.isEmpty()) PENDING.remove(world);
    }

    private static boolean craft(WorldServer world, EntityItem trigger) {
        BlockPos position = trigger.getPosition();
        List<CustomRecipe> recipes = RecipeStore.list(CraftKind.DROP);
        if (recipes.isEmpty()) return false;
        boolean crafted = false;
        for (int chain = 0; chain < 64; chain++) {
            List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(position)).stream()
                    .filter(item -> !item.isDead && ELIGIBLE.contains(item) && !item.getItem().isEmpty()).collect(Collectors.toList());
            if (items.size() < 2) return crafted;
            CustomRecipe recipe = null;
            int[] deduction = null;
            for (CustomRecipe candidate : recipes) {
                if (!bound(candidate, world, position)) continue;
                int[] plan = plan(items, candidate);
                if (plan != null) { recipe = candidate; deduction = plan; break; }
            }
            if (recipe == null) return crafted;
            EntityItem output = new EntityItem(world, position.getX() + .5, position.getY() + .2, position.getZ() + .5, recipe.result.copy());
            output.setDefaultPickupDelay();
            // EntityJoinWorldEvent 取消生成时保留全部原料。
            if (!world.spawnEntity(output)) return crafted;
            for (int i = 0; i < items.size(); i++) if (deduction[i] > 0) {
                EntityItem item = items.get(i);
                ItemStack remaining = item.getItem().copy();
                remaining.shrink(deduction[i]);
                if (remaining.isEmpty()) item.setDead();
                else item.setItem(remaining);
            }
            crafted = true;
            FxEngine.spawn(world, trigger.posX, trigger.posY, trigger.posZ, recipe.effects);
            if (!recipe.sound.isEmpty()) {
                try {
                    world.getMinecraftServer().getPlayerList().sendToAllNearExcept(null, trigger.posX, trigger.posY, trigger.posZ,
                            32, world.provider.getDimension(), new SPacketCustomSound(new ResourceLocation(recipe.sound).toString(),
                                    SoundCategory.NEUTRAL, trigger.posX, trigger.posY, trigger.posZ, 1, 1));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return crafted;
    }

    private static int[] plan(List<EntityItem> items, CustomRecipe recipe) {
        int[] stock = new int[items.size()], needed = new int[recipe.inputs.length];
        boolean[][] matches = new boolean[needed.length][stock.length];
        for (int i = 0; i < stock.length; i++) stock[i] = items.get(i).getItem().getCount();
        for (int i = 0; i < needed.length; i++) {
            ItemStack input = recipe.inputs[i];
            needed[i] = input.isEmpty() ? 0 : input.getCount();
            for (int j = 0; j < stock.length; j++) matches[i][j] = CustomRecipe.matches(items.get(j).getItem(), input, recipe.nameOnly);
        }
        return CraftPlanner.plan(stock, needed, matches);
    }

    private static boolean bound(CustomRecipe recipe, WorldServer world, BlockPos pos) {
        if (recipe.dimension.isEmpty()) return true;
        String dimension = Integer.toString(world.provider.getDimension());
        if (!recipe.dimension.equals(dimension)
                && !("0".equals(dimension) && "minecraft:overworld".equals(recipe.dimension))
                && !("-1".equals(dimension) && "minecraft:the_nether".equals(recipe.dimension))
                && !("1".equals(dimension) && "minecraft:the_end".equals(recipe.dimension))) return false;
        return !recipe.bindPosition || recipe.x == pos.getX() && recipe.y == pos.getY() && recipe.z == pos.getZ();
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof WorldServer) {
            PENDING.remove(event.getWorld());
            ELIGIBLE.removeIf(item -> item.world == event.getWorld());
        }
    }
    public static void clear() { PENDING.clear(); ELIGIBLE.clear(); }
}
