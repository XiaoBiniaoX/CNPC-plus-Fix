package top.cnpcplus.mixin;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.lines.LinesData;

@Mixin(value = Lines.class, remap = false)
public class MixinLinesArrays {
    @Shadow public HashMap<Integer, Line> lines;
    @Shadow private int lastLine;
    @Inject(method = "save", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$save(CallbackInfoReturnable<CompoundTag> cir) { cir.setReturnValue(LinesData.save(lines)); }
    @Inject(method = "readNBT", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$read(CompoundTag tag, CallbackInfo ci) {
        lines = LinesData.read(tag);
        lastLine = -1;
        ci.cancel();
    }
    @Inject(method = "getLine", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$choose(boolean random, CallbackInfoReturnable<Line> cir) {
        List<Integer> slots = LinesData.slots(lines);
        if (slots.isEmpty()) { cir.setReturnValue(null); return; }
        int slot = random ? slots.get(ThreadLocalRandom.current().nextInt(slots.size())) : LinesData.nextSlot(slots, lastLine);
        if (!random) lastLine = slot;
        cir.setReturnValue(lines.get(slot).copy());
    }
}
