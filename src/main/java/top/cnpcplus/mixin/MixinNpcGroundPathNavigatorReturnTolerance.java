package top.cnpcplus.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.ai.NpcGroundPathNavigator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import top.cnpcplus.ai.ReturnHomeState;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/** Tightens the NPC ground navigator's final waypoint tolerance during return-home. */
@Mixin(value = NpcGroundPathNavigator.class, remap = false)
public abstract class MixinNpcGroundPathNavigatorReturnTolerance extends PathNavigation {

    protected MixinNpcGroundPathNavigatorReturnTolerance(Mob mob, Level level) {
        super(mob, level);
    }

    @ModifyConstant(method = "m_7636_", constant = @Constant(floatValue = 0.75F, ordinal = 1),
            require = 1, remap = false)
    private float cnpcplus$preciseReturnArrival(float original) {
        if (this.mob instanceof EntityNPCInterface npc
                && ReturnHomeState.isActive(npc)
                && ServerConfigAccess.bool(CnpcPlusServerConfig.ReturnHomeSmoothArrival, true)) {
            // NpcGroundPathNavigator computes 0.75 - width/2. For a normal
            // width of 0.6 that is 0.45; 0.50 gives the exact ±0.2 target
            // tolerance used by EntityNPCInterface.isVeryNearAssignedPlace().
            return 0.50F;
        }
        return original;
    }
}
