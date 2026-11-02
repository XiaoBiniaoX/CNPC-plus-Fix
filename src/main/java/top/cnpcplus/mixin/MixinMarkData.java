package top.cnpcplus.mixin;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.controllers.data.MarkData;
import noppes.npcs.entity.data.IEntityPersistentData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.marks.MarkFallbackAccess;
import top.cnpcplus.marks.MarkRotation;

@Mixin(value = MarkData.class, remap = false)
public abstract class MixinMarkData {
    @Shadow public List<MarkData.Mark> marks;
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$entityOwned(LivingEntity entity, CallbackInfoReturnable<MarkData> cir) {
        MarkFallbackAccess access = (MarkFallbackAccess)entity;
        MarkData data = access.cnpcplus$markFallback();
        if (data == null) {
            data = new MarkData(); ((MarkDataAccess)data).cnpcplus$entity(entity);
            data.setNBT(((IEntityPersistentData)entity).getPersistentData().getCompound("cnpcmarkdata"));
            access.cnpcplus$markFallback(data);
        }
        cir.setReturnValue(data);
    }
    @Inject(method = "setNBT", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$validList(CompoundTag tag, CallbackInfo ci) {
        if (tag == null || tag.contains("marks") && (!(tag.get("marks") instanceof ListTag list)
                || !list.isEmpty() && list.getElementType() != 10)) ci.cancel();
    }
    @Inject(method = "setNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readRotation(CompoundTag tag, CallbackInfo ci) {
        ListTag list = tag.getList("marks", 10);
        for (int i = 0; i < marks.size(); i++) MarkRotation.read(marks.get(i), list.getCompound(i));
    }
    @Inject(method = "getNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeRotation(CallbackInfoReturnable<CompoundTag> cir) {
        ListTag list = cir.getReturnValue().getList("marks", 10);
        for (int i = 0; i < marks.size(); i++) MarkRotation.write(marks.get(i), list.getCompound(i));
    }
}
