package top.cnpcplus.script;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

public final class ScriptParticles {
    private ScriptParticles() {}

    public static void dust(ServerLevel level, float red, float green, float blue, float scale,
                            double x, double y, double z, double dx, double dy, double dz,
                            double speed, int count) {
        spawn(level, new DustParticleOptions(new Vector3f(red, green, blue), scale),
                x, y, z, dx, dy, dz, speed, count);
    }

    public static void block(ServerLevel level, String blockId,
                             double x, double y, double z, double dx, double dy, double dz,
                             double speed, int count) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (block == null) throw new IllegalArgumentException("Unknown block: " + blockId);
        spawn(level, new BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, block.defaultBlockState()),
                x, y, z, dx, dy, dz, speed, count);
    }

    public static void item(ServerLevel level, String itemId,
                            double x, double y, double z, double dx, double dy, double dz,
                            double speed, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        if (item == null) throw new IllegalArgumentException("Unknown item: " + itemId);
        spawn(level, new ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, new ItemStack(item)),
                x, y, z, dx, dy, dz, speed, count);
    }

    private static void spawn(ServerLevel level, ParticleOptions particle,
                              double x, double y, double z, double dx, double dy, double dz,
                              double speed, int count) {
        level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }
}
