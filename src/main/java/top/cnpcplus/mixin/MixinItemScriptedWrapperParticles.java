package top.cnpcplus.mixin;

import net.minecraft.server.level.ServerLevel;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcplus.script.ScriptParticles;

@Mixin(value = ItemScriptedWrapper.class, remap = false)
public class MixinItemScriptedWrapperParticles {
    @Unique
    public void spawnDustParticle(IPlayer player, float red, float green, float blue, float scale,
                                  double x, double y, double z, double dx, double dy, double dz,
                                  double speed, int count) {
        ScriptParticles.dust((ServerLevel) player.getMCEntity().level(), red, green, blue, scale,
                x, y, z, dx, dy, dz, speed, count);
    }

    @Unique
    public void spawnBlockParticle(IPlayer player, String block, double x, double y, double z,
                                   double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.block((ServerLevel) player.getMCEntity().level(), block, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique
    public void spawnItemParticle(IPlayer player, String item, double x, double y, double z,
                                  double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.item((ServerLevel) player.getMCEntity().level(), item, x, y, z, dx, dy, dz, speed, count);
    }
}
