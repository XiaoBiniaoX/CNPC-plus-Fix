package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.persist.NpcDataExternalStore;

@Mixin(value = EntityCustomNpc.class, remap = false)
public abstract class MixinEntityCustomNpcModelStore {
    // CustomNpc reads its model before super reads the entity UUID.
    @Inject(method = "readAdditionalSaveData", at = @At("HEAD"), require = 1)
    private void cnpcplus$restoreModel(CompoundTag tag, CallbackInfo ci) {
        NpcDataExternalStore.MODEL.prepareEarlyLoad((EntityCustomNpc) (Object) this, tag);
    }

    // The entire model, including injected scale/visibility fields, is written after super.
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"), require = 1)
    private void cnpcplus$storeWholeModel(CompoundTag tag, CallbackInfo ci) {
        NpcDataExternalStore.MODEL.saveNpc((EntityCustomNpc) (Object) this, tag);
    }
}
