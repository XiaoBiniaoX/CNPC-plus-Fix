package bin.cnpcplus.mixin.playerdata;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.PlayerBankData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PlayerBankData.loadNBTData 键缺失保护，防止银行数据被空 map 覆盖。
 */
@Mixin(value = PlayerBankData.class, remap = false)
public class MixinPlayerBankDataKeyGuard {

    @Inject(method = "loadNBTData", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$guardMissingKey(HolderLookup.Provider lookupProvider, CompoundTag compound, CallbackInfo ci) {
        if (compound == null) return;
        if (!compound.contains("BankData")) {
            ci.cancel();
        }
    }
}
