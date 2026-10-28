package bin.cnpcplus.mixin.follower;

import bin.cnpcplus.common.IRoleFollowerOwnerAccess;
import noppes.npcs.roles.RoleFollower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = RoleFollower.class, remap = false)
public abstract class MixinRoleFollowerOwner implements IRoleFollowerOwnerAccess {
    @Shadow private String ownerUUID;

    @Override
    public String cnpcplus$getOwnerUUID() {
        return this.ownerUUID;
    }
}
