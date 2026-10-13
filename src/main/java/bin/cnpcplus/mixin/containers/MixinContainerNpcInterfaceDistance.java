package bin.cnpcplus.mixin.containers;

import net.minecraft.world.entity.player.Player;
import noppes.npcs.containers.ContainerNpcInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ContainerNpcInterface.stillValid 改用距离容差，防止移动1格即强制关闭容器。
 *
 * <p>CNPC 原版用整数坐标精确匹配（posX == floor(player.getX())），
 * 玩家微小移动（亚格位移导致 floor 变化）就触发容器关闭。
 * 此处改为 8 格距离容差。
 */
@Mixin(value = ContainerNpcInterface.class, remap = false)
public class MixinContainerNpcInterfaceDistance {

    @Shadow(remap = false)
    private int posX;

    @Shadow(remap = false)
    private int posZ;

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$distanceTolerance(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player.isRemoved()) {
            cir.setReturnValue(false);
            return;
        }
        double dx = player.getX() - this.posX;
        double dz = player.getZ() - this.posZ;
        cir.setReturnValue((dx * dx + dz * dz) <= 64.0); // 8格容差
    }
}
