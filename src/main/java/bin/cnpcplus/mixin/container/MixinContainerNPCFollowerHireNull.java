package bin.cnpcplus.mixin.container;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = noppes.npcs.containers.ContainerNPCFollowerHire.class, remap = false)
abstract class MixinContainerNPCFollowerHireNull {

    @Inject(method = "<init>", at = @At("HEAD"), remap = false)
    private static void cnpcplus$nullGuard(int containerId, net.minecraft.world.entity.player.Inventory playerInventory, int entityId, CallbackInfo ci) {
        Entity entity = playerInventory.player.level().getEntity(entityId);
        if (entity == null) {
            throw new IllegalStateException("CNPC: ContainerNPCFollowerHire 实体 " + entityId + " 未加载，无法打开容器");
        }
    }
}
