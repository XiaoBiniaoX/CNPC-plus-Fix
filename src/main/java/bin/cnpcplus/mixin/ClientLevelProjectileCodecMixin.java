package bin.cnpcplus.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CNPC's own ClientPlayNetHandlerMixin discards the EntityProjectile that vanilla built in
 * handleAddEntity and adds a second, hand-made one. That replacement never goes through
 * Entity#recreateFromPacket, so its packetPositionCodec base stays at (0,0,0).
 *
 * The first ClientboundMoveEntityPacket.Pos (a relative delta) is then decoded against a zero
 * base, teleporting the projectile to the world origin roughly one tick after spawn - which is
 * why it was only ever visible for the single tick the throw sound played, in both 2D and 3D
 * mode, and why "sticks to walls" never showed anything either.
 *
 * Re-seeding the codec base right before the entity enters the client level is enough: the client
 * then simulates the real trajectory, and EntityProjectile#onHit sets inGround client-side so the
 * projectile stays on the wall.
 */
@Mixin(ClientLevel.class)
public class ClientLevelProjectileCodecMixin {
    @Inject(method = "addEntity(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), remap = false)
    private void cnpcplus$seedProjectilePositionCodec(Entity entity, CallbackInfo ci) {
        if (entity instanceof EntityProjectile) {
            entity.syncPacketPositionCodec(entity.getX(), entity.getY(), entity.getZ());
        }
    }
}
