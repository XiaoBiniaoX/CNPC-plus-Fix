package bin.cnpcplus.mixin.follower;

import bin.cnpcplus.follower.FollowerMessages;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.ITextComponent;
import noppes.npcs.roles.RoleFollower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = RoleFollower.class, remap = false)
public abstract class MixinRoleFollowerMessages {
    @Inject(method = "readFromNBT", at = @At("TAIL"), require = 1)
    private void cnpcplus$load(NBTTagCompound tag, CallbackInfo ci) { FollowerMessages.load((RoleFollower) (Object) this, tag); }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$save(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) { FollowerMessages.get((RoleFollower) (Object) this).write(cir.getReturnValue()); }
    @Redirect(method = "aiShouldExecute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;func_145747_a(Lnet/minecraft/util/text/ITextComponent;)V"), require = 1)
    private void cnpcplus$farewell(EntityPlayer player, ITextComponent original) {
        RoleFollower role = (RoleFollower) (Object) this;
        if (player instanceof EntityPlayerMP) FollowerMessages.say(role, (EntityPlayerMP) player, role.dialogFarewell);
    }
}
