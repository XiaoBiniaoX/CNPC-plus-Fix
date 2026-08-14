package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcplus.script.ScriptParticles;

@Mixin(value = EntityWrapper.class, remap = false)
public class MixinEntityWrapperParticles {
    @Shadow(remap = false) protected Entity entity;

    @Unique
    public void spawnDustParticle(float red, float green, float blue, float scale, double x, double y, double z,
                                  double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.dust((ServerLevel) entity.level(), red, green, blue, scale, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique
    public void spawnBlockParticle(String block, double x, double y, double z,
                                   double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.block((ServerLevel) entity.level(), block, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique
    public void spawnItemParticle(String item, double x, double y, double z,
                                  double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.item((ServerLevel) entity.level(), item, x, y, z, dx, dy, dz, speed, count);
    }
}
