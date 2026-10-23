package top.cnpcplus.mixin;

import noppes.npcs.roles.RoleFollower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.cnpcplus.accessor.RoleFollowerOwnerAccess;

@Mixin(value = RoleFollower.class, remap = false)
public abstract class MixinRoleFollowerOwnerAccessor implements RoleFollowerOwnerAccess {
    @Accessor("ownerUUID")
    public abstract String cnpcplus$getOwnerUUID();
}
