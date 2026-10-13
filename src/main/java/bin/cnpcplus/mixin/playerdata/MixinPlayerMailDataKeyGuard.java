package bin.cnpcplus.mixin.playerdata;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerMailData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerMailData.loadNBTData 键缺失保护，防止邮件被空列表覆盖。
 */
@Mixin(value = PlayerMailData.class, remap = false)
public class MixinPlayerMailDataKeyGuard {

    @Inject(method = "loadNBTData", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$guardMissingKey(CompoundTag compound, CallbackInfo ci) {
        if (compound == null) return;
        if (!compound.contains("MailData")) {
            ci.cancel();
        }
    }
}
