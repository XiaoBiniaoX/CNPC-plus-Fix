package bin.cnpcplus.mixin.container;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 容器构造时实体未加载的空判，防止客户端 NPE 崩溃。
 * NPC 假死/跨维度/区块未加载时 getEntity 返回 null，原版直接强转后 NPE。
 */
@Mixin(value = noppes.npcs.containers.ContainerNPCCompanion.class, remap = false)
abstract class MixinContainerNPCCompanionNull {

    @Inject(method = "<init>", at = @At("HEAD"), remap = false)
    private static void cnpcplus$nullGuard(int containerId, net.minecraft.world.entity.player.Inventory playerInventory, int entityId, CallbackInfo ci) {
        Entity entity = playerInventory.player.level().getEntity(entityId);
        if (entity == null) {
            throw new IllegalStateException("CNPC: ContainerNPCCompanion 实体 " + entityId + " 未加载，无法打开容器");
        }
    }
}
