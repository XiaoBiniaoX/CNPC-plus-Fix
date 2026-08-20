package bin.cnpcplus.mixin.lifecycle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNpcCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityNpcCrystal.class, remap = false)
public class MixinEntityNpcCrystalMount {
    @Redirect(method = "func_70071_h_", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_72838_d(Lnet/minecraft/entity/Entity;)Z"), remap = false)
    private boolean cnpcplus$transferMounts(World world, Entity replacement) {
        EntityNpcCrystal old = (EntityNpcCrystal) (Object) this;
        Entity oldEntity = (Entity) old;
        Entity vehicle = oldEntity.getRidingEntity();
        List<Entity> passengers = new ArrayList<Entity>(oldEntity.getPassengers());
        oldEntity.dismountRidingEntity();
        oldEntity.removePassengers();
        boolean spawned = world.spawnEntity(replacement);
        if (!spawned || replacement.world != world) {
            if (vehicle != null && vehicle.world == world && vehicle.isEntityAlive()) {
                oldEntity.startRiding(vehicle, true);
            }
            for (Entity passenger : passengers) {
                if (passenger.world == world && passenger.isEntityAlive()) {
                    passenger.startRiding(oldEntity, true);
                }
            }
            return spawned;
        }
        if (vehicle != null && vehicle != replacement && vehicle.world == world && vehicle.isEntityAlive()) {
            replacement.startRiding(vehicle, true);
        }
        for (Entity passenger : passengers) {
            if (passenger != replacement && passenger.world == world && passenger.isEntityAlive()) {
                passenger.startRiding(replacement, true);
            }
        }
        return true;
    }
}
