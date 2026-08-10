package bin.cnpcplus.mixin.animation;

import bin.cnpcplus.animation.SwingDriver;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumHand;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cannot mixin EntityLivingBase directly (loaded before our mixin config
 * prepares, MixinTargetAlreadyLoadedException). Instead redirect the swingArm
 * invoke inside NetHandlerPlayClient.handleAnimation (loaded late, on world
 * join): each swing packet for an outer NPC starts/restarts the synthetic
 * swing in SwingDriver. This is the client trigger for both AI attacks and
 * script swingMainhand() (both go through SPacketAnimation).
 */
@Mixin(value = NetHandlerPlayClient.class, remap = false)
public class MixinNetHandlerSwing {

    @Redirect(method = "func_147279_a",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/EntityLivingBase;func_184609_a(Lnet/minecraft/util/EnumHand;)V"),
            remap = false)
    private void cnpcplus$onSwing(EntityLivingBase entity, EnumHand hand) {
        if (entity instanceof EntityCustomNpc && entity.world.isRemote) {
            SwingDriver.startSwing(entity);
        }
        entity.swingArm(hand);
    }
}
