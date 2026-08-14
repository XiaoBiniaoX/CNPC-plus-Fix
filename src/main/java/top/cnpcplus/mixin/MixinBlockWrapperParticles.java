package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.wrapper.BlockWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcplus.script.ScriptParticles;

@Mixin(value = BlockWrapper.class, remap = false)
public class MixinBlockWrapperParticles {
    @Shadow(remap = false) protected IWorld level;

    @Unique
    public void spawnDustParticle(float red, float green, float blue, float scale, double x, double y, double z,
                                  double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.dust((ServerLevel) level.getMCLevel(), red, green, blue, scale, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique
    public void spawnBlockParticle(String block, double x, double y, double z,
                                   double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.block((ServerLevel) level.getMCLevel(), block, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique
    public void spawnItemParticle(String item, double x, double y, double z,
                                  double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.item((ServerLevel) level.getMCLevel(), item, x, y, z, dx, dy, dz, speed, count);
    }
}
