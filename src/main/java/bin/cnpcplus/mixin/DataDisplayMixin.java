package bin.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataDisplay.class, priority = 500)
public class DataDisplayMixin {

    @Shadow(remap = false)
    EntityNPCInterface npc;

    @Inject(method = "readToNBT", at = @At("HEAD"), remap = false)
    private void cnpcplus$stripEfModelWhenDead(CompoundTag nbttagcompound, CallbackInfo ci) {
        if (npc.isKilled() && nbttagcompound.contains("efModel")) {
            nbttagcompound.remove("efModel");
        }
    }
}