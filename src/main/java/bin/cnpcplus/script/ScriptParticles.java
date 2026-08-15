package bin.cnpcplus.script;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;
import noppes.npcs.api.CustomNPCsException;

public final class ScriptParticles {
    private ScriptParticles() {}

    public static void dust(WorldServer world, float red, float green, float blue, float scale,
                            double x, double y, double z, double dx, double dy, double dz,
                            double speed, int count) {
        world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, count,
                red, green, blue, scale, new int[0]);
    }

    public static void block(WorldServer world, String blockName, double x, double y, double z,
                             double dx, double dy, double dz, double speed, int count) {
        Block block = Block.getBlockFromName(blockName);
        if (block == null) {
            throw new CustomNPCsException("Unknown block: " + blockName, new Object[0]);
        }
        world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, x, y, z, count,
                dx, dy, dz, speed, Block.getStateId(block.getDefaultState()));
    }

    public static void item(WorldServer world, String itemName, double x, double y, double z,
                            double dx, double dy, double dz, double speed, int count) {
        Item item = Item.getByNameOrId(itemName);
        if (item == null) {
            throw new CustomNPCsException("Unknown item: " + itemName, new Object[0]);
        }
        world.spawnParticle(EnumParticleTypes.ITEM_CRACK, x, y, z, count,
                dx, dy, dz, speed, Item.getIdFromItem(item), 0);
    }
}
