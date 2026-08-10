package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryOffset implements RecipeCarpentryOffsetAccessor {

    @Unique private int cnpcplusOffsetX;
    @Unique private int cnpcplusOffsetY;
    @Unique private boolean cnpcplusHasOffset;

    @Override
    public int cnpcplusGetOffsetX() {
        return cnpcplusOffsetX;
    }

    @Override
    public int cnpcplusGetOffsetY() {
        return cnpcplusOffsetY;
    }

    @Override
    public boolean cnpcplusHasSavedOffset() {
        return cnpcplusHasOffset;
    }

    @Override
    public void cnpcplusSetOffset(int offsetX, int offsetY, boolean saved) {
        this.cnpcplusOffsetX = offsetX;
        this.cnpcplusOffsetY = offsetY;
        this.cnpcplusHasOffset = saved;
    }

    @Inject(method = "read", at = @At("RETURN"), remap = false)
    private static void cnpcplusReadOffset(NBTTagCompound compound, CallbackInfoReturnable<RecipeCarpentry> cir) {
        RecipeCarpentry recipe = cir.getReturnValue();
        if (recipe == null || compound == null) return;
        if (!compound.hasKey("cnpcplus_offsetX") && !compound.hasKey("cnpcplus_offsetY")) return;
        ((RecipeCarpentryOffsetAccessor) recipe).cnpcplusSetOffset(
                compound.getInteger("cnpcplus_offsetX"),
                compound.getInteger("cnpcplus_offsetY"),
                true
        );
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplusWriteOffset(CallbackInfoReturnable<NBTTagCompound> cir) {
        if (!this.cnpcplusHasOffset) return;
        NBTTagCompound tag = cir.getReturnValue();
        if (tag == null) return;
        tag.setInteger("cnpcplus_offsetX", this.cnpcplusOffsetX);
        tag.setInteger("cnpcplus_offsetY", this.cnpcplusOffsetY);
    }

    @Inject(method = "copy", at = @At("RETURN"), remap = false)
    private void cnpcplusCopyOffset(RecipeCarpentry from, CallbackInfo ci) {
        RecipeCarpentryOffsetAccessor src = (RecipeCarpentryOffsetAccessor) from;
        this.cnpcplusSetOffset(src.cnpcplusGetOffsetX(), src.cnpcplusGetOffsetY(), src.cnpcplusHasSavedOffset());
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        if ((self.name == null || self.name.isEmpty()) && from.name != null) {
            self.name = from.name;
        }
    }
}
