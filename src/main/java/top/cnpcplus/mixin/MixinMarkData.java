package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.LazyOptional;
import noppes.npcs.controllers.data.MarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.marks.MarkFallbackAccess;
import top.cnpcplus.marks.MarkRotation;
import java.util.List;

@Mixin(value = MarkData.class, remap = false)
public abstract class MixinMarkData {
    @Shadow private LivingEntity entity;
    @Shadow public List<MarkData.Mark> marks;

    @Redirect(method = "get", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/common/util/LazyOptional;orElse(Ljava/lang/Object;)Ljava/lang/Object;"), require = 1)
    private static Object cnpcplus$independentFallback(LazyOptional<MarkData> capability, Object backup, LivingEntity entity) {
        MarkData data = capability.orElse(null);
        if (data == null) {
            MarkFallbackAccess access = (MarkFallbackAccess)entity;
            data = access.cnpcplus$markFallback();
            if (data == null) {
                data = new MarkData();
                access.cnpcplus$markFallback(data);
            }
        }
        return data; // Original get() still binds the entity and loads its own persistent NBT.
    }

    @Inject(method = "setNBT", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$rejectMissingList(CompoundTag tag, CallbackInfo ci) {
        // Absent marks means a legacy NPC with no marks. Reject malformed payloads, not legacy absence.
        if (tag == null || tag.contains("marks") && (!(tag.get("marks") instanceof ListTag list)
                || !list.isEmpty() && list.getElementType() != 10)) ci.cancel();
    }

    @Inject(method = "setNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readRotation(CompoundTag tag, CallbackInfo ci) {
        if (tag == null) return;
        ListTag list = tag.getList("marks", 10);
        for (int i = 0; i < Math.min(marks.size(), list.size()); i++) MarkRotation.read(marks.get(i), list.getCompound(i));
    }

    @Inject(method = "getNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeRotation(CallbackInfoReturnable<CompoundTag> cir) {
        ListTag list = cir.getReturnValue().getList("marks", 10);
        for (int i = 0; i < Math.min(marks.size(), list.size()); i++) MarkRotation.write(marks.get(i), list.getCompound(i));
    }

    @Inject(method = {"save", "syncClients"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void cnpcplus$unboundDraft(CallbackInfo ci) { if (entity == null) ci.cancel(); }
}
