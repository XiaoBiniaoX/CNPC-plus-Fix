package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptParticles;
import net.minecraft.world.WorldServer;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.wrapper.BlockWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = BlockWrapper.class, remap = false)
public class MixinBlockWrapperParticles {
    @Shadow(remap = false) protected IWorld world;

    @Unique public void spawnDustParticle(float red, float green, float blue, float scale, double x, double y, double z,
                                          double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.dust((WorldServer) this.world.getMCWorld(), red, green, blue, scale, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique public void spawnBlockParticle(String block, double x, double y, double z,
                                           double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.block((WorldServer) this.world.getMCWorld(), block, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique public void spawnItemParticle(String item, double x, double y, double z,
                                          double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.item((WorldServer) this.world.getMCWorld(), item, x, y, z, dx, dy, dz, speed, count);
    }
}
