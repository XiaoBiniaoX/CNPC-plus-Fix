package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptParticles;
import net.minecraft.entity.Entity;
import net.minecraft.world.WorldServer;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = EntityWrapper.class, remap = false)
public class MixinEntityWrapperParticles {
    @Shadow(remap = false) protected Entity entity;

    @Unique public void spawnDustParticle(float red, float green, float blue, float scale, double x, double y, double z,
                                          double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.dust((WorldServer) this.entity.world, red, green, blue, scale, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique public void spawnBlockParticle(String block, double x, double y, double z,
                                           double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.block((WorldServer) this.entity.world, block, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique public void spawnItemParticle(String item, double x, double y, double z,
                                          double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.item((WorldServer) this.entity.world, item, x, y, z, dx, dy, dz, speed, count);
    }
}
