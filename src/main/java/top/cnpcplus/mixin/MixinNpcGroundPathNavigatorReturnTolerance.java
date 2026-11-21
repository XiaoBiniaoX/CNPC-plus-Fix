package top.cnpcplus.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.ai.NpcGroundPathNavigator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import top.cnpcplus.ai.ReturnHomeState;

/** Match the waypoint center used by Path.getEntityPosAtNode during returns. */
@Mixin(value = NpcGroundPathNavigator.class, remap = false)
public abstract class MixinNpcGroundPathNavigatorReturnTolerance extends PathNavigation {

    protected MixinNpcGroundPathNavigatorReturnTolerance(Mob mob, Level level) {
        super(mob, level);
    }

    @ModifyConstant(method = "m_7636_", constant = @Constant(doubleValue = 0.5), require = 2)
    private double cnpcplus$returnWaypointCenter(double original) {
        if (mob instanceof EntityNPCInterface npc && ReturnHomeState.target(npc) != null) {
            return (int)(mob.getBbWidth() + 1.0F) * 0.5;
        }
        return original;
    }
}
