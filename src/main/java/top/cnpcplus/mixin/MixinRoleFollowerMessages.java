package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleFollower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.follower.FollowerMessages;

@Mixin(value = RoleFollower.class, remap = false)
public abstract class MixinRoleFollowerMessages {
    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void cnpcplus$loadMessages(CompoundTag tag, CallbackInfo ci) { FollowerMessages.load((RoleFollower) (Object) this, tag); }

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveMessages(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        FollowerMessages.get((RoleFollower) (Object) this).write(cir.getReturnValue());
    }

    @Redirect(method = "aiShouldExecute", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/EntityNPCInterface;say(Lnet/minecraft/world/entity/player/Player;Lnoppes/npcs/controllers/data/Line;)V"), require = 1)
    private void cnpcplus$farewellWithoutLos(EntityNPCInterface npc, Player player, Line line) {
        RoleFollower role = (RoleFollower) (Object) this;
        if (player instanceof ServerPlayer serverPlayer) FollowerMessages.say(role, serverPlayer,
                role.dialogFarewell.isEmpty() ? "follower.farewellText" : role.dialogFarewell);
    }
}
