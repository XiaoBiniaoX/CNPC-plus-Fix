package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.packets.client.PacketSync;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketSync.class, remap = false)
public abstract class MixinPacketSyncRecipeList {
    @Shadow @Final private int type;
    @Shadow @Final private CompoundTag data;
    @Shadow @Final private boolean syncEnd;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$recipes(CallbackInfo ci) {
        if (type != 6 && type != 7) return;
        bin.cnpcplus.craftingview.RecipeAccess.acceptList(type, data, syncEnd);
        ci.cancel();
    }
}
