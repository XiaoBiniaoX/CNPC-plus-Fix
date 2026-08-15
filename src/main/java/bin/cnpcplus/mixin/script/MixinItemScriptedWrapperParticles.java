package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptParticles;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ItemScriptedWrapper.class, remap = false)
public class MixinItemScriptedWrapperParticles {
    @Unique public void spawnDustParticle(IPlayer player, float red, float green, float blue, float scale,
                                          double x, double y, double z, double dx, double dy, double dz,
                                          double speed, int count) {
        ScriptParticles.dust(((EntityPlayerMP) player.getMCEntity()).getServerWorld(), red, green, blue, scale,
                x, y, z, dx, dy, dz, speed, count);
    }

    @Unique public void spawnBlockParticle(IPlayer player, String block, double x, double y, double z,
                                           double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.block(((EntityPlayerMP) player.getMCEntity()).getServerWorld(), block, x, y, z, dx, dy, dz, speed, count);
    }

    @Unique public void spawnItemParticle(IPlayer player, String item, double x, double y, double z,
                                          double dx, double dy, double dz, double speed, int count) {
        ScriptParticles.item(((EntityPlayerMP) player.getMCEntity()).getServerWorld(), item, x, y, z, dx, dy, dz, speed, count);
    }
}
