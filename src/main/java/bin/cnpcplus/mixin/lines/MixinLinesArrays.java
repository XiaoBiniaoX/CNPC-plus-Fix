package bin.cnpcplus.mixin.lines;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import bin.cnpcplus.lines.ArrayLine;
import bin.cnpcplus.lines.LinesData;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Lines.class, remap = false)
public abstract class MixinLinesArrays {
    @Shadow public HashMap<Integer, Line> lines;
    @Shadow private int lastLine;
    @Inject(method = "writeToNBT", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$save(CallbackInfoReturnable<NBTTagCompound> cir) { cir.setReturnValue(LinesData.save(lines)); }
    @Inject(method = "readNBT", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$read(NBTTagCompound tag, CallbackInfo ci) {
        lines = LinesData.read(tag); lastLine = -1; ci.cancel();
    }
    @Inject(method = "getLine", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$choose(boolean random, CallbackInfoReturnable<Line> cir) {
        List<Integer> slots = LinesData.slots(lines);
        if (slots.isEmpty()) { cir.setReturnValue(null); return; }
        int slot = random ? slots.get(ThreadLocalRandom.current().nextInt(slots.size()))
                : slots.stream().filter(value -> value > lastLine).findFirst().orElse(slots.get(0));
        if (!random) lastLine = slot;
        cir.setReturnValue(ArrayLine.of(lines.get(slot)).forPlayback());
    }
}
