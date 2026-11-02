package bin.cnpcplus.mixin.marks;

import bin.cnpcplus.marks.MarkRotation;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.data.MarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MarkData.class, remap = false)
public abstract class MixinMarkData {
    @Shadow public List<MarkData.Mark> marks;
    @Inject(method = "setNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$read(NBTTagCompound tag, CallbackInfo ci) {
        NBTTagList list = tag.getTagList("marks", 10);
        for (int i = 0; i < marks.size(); i++) MarkRotation.read(marks.get(i), list.getCompoundTagAt(i));
    }
    @Inject(method = "getNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$write(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagList list = cir.getReturnValue().getTagList("marks", 10);
        for (int i = 0; i < marks.size(); i++) MarkRotation.write(marks.get(i), list.getCompoundTagAt(i));
    }
}
