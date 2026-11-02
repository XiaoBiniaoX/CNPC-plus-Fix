package top.cnpcplus.script;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

public final class ScriptParticles {
    private ScriptParticles() {}

    public static void dust(ServerLevel level, float red, float green, float blue, float scale,
                            double x, double y, double z, double dx, double dy, double dz,
                            double speed, int count) {
        level.sendParticles(new DustParticleOptions(new Vector3f(red, green, blue), scale),
                x, y, z, count, dx, dy, dz, speed);
    }

    public static void block(ServerLevel level, String blockId,
                             double x, double y, double z, double dx, double dy, double dz,
                             double speed, int count) {
        var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(blockId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown block: " + blockId));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.defaultBlockState()),
                x, y, z, count, dx, dy, dz, speed);
    }

    public static void item(ServerLevel level, String itemId,
                            double x, double y, double z, double dx, double dy, double dz,
                            double speed, int count) {
        var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(itemId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + itemId));
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(item)),
                x, y, z, count, dx, dy, dz, speed);
    }
}
