package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.data.NpcHitboxStore;

@Mixin(value = DataDisplay.class, remap = false)
public abstract class MixinDataDisplayHitbox {
    @Shadow EntityNPCInterface npc;

    // Native readToNBT refreshes dimensions at the end, so configuration must precede it.
    @Inject(method = "readToNBT", at = @At("HEAD"), require = 1)
    private void cnpcplus$readHitbox(CompoundTag tag, CallbackInfo ci) {
        NpcHitboxStore.read((DataDisplay) (Object) this, tag);
    }

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveHitbox(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        NpcHitboxStore.write((DataDisplay) (Object) this, cir.getReturnValue());
    }

    @Inject(method = "setSize", at = @At("TAIL"), require = 1)
    private void cnpcplus$refreshSize(float size, CallbackInfo ci) {
        cnpcplus$refreshHitbox();
    }

    @Inject(method = "setHitboxState", at = @At("TAIL"), require = 1)
    private void cnpcplus$refreshState(byte state, CallbackInfo ci) {
        cnpcplus$refreshHitbox();
    }

    private void cnpcplus$refreshHitbox() {
        if (npc == null || npc.display == null || npc.level() == null) return;
        double x = npc.getX(), y = npc.getY(), z = npc.getZ();
        npc.refreshDimensions();
        npc.setPos(x, y, z);
    }
}
