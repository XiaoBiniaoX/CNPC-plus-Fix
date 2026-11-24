package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.recipe.RecipeCarpentryOffsetAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stores placement offset so cropped recipes reopen at original grid position.
 * Ported behavior from 1.20.1 cnpcplus (not 1.20 source dump).
 */
@Mixin(RecipeCarpentry.class)
public class MixinRecipeCarpentryOffset implements RecipeCarpentryOffsetAccessor {
    public net.minecraft.world.item.crafting.RecipeSerializer<?> getSerializer() {
        return top.cnpcplus.smithing.SmithingSerializers.CARPENTRY.get();
    }

    @Unique private int cnpcplusOffsetX;
    @Unique private int cnpcplusOffsetY;
    @Unique private boolean cnpcplusHasOffset;
    @Unique private boolean cnpcplus$shapeless;
    @Override public boolean cnpcplus$isShapeless() { return cnpcplus$shapeless; }
    @Override public void cnpcplus$setShapeless(boolean value) { cnpcplus$shapeless = value; }

    @Override
    public int cnpcplusGetOffsetX() { return cnpcplusOffsetX; }

    @Override
    public int cnpcplusGetOffsetY() { return cnpcplusOffsetY; }

    @Override
    public boolean cnpcplusHasSavedOffset() { return cnpcplusHasOffset; }

    @Override
    public void cnpcplusSetOffset(int offsetX, int offsetY, boolean saved) {
        this.cnpcplusOffsetX = offsetX;
        this.cnpcplusOffsetY = offsetY;
        this.cnpcplusHasOffset = saved;
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void cnpcplusReadOffset(CompoundTag compound, HolderLookup.Provider provider, CallbackInfoReturnable<RecipeCarpentry> cir) {
        RecipeCarpentry recipe = cir.getReturnValue();
        if (recipe == null || compound == null) return;
        ((RecipeCarpentryOffsetAccessor)recipe).cnpcplus$setShapeless(compound.getBoolean("CNPCPlusShapeless"));
        if (!compound.contains("cnpcplus_offsetX") && !compound.contains("cnpcplus_offsetY")) return;
        ((RecipeCarpentryOffsetAccessor) recipe).cnpcplusSetOffset(
                compound.getInt("cnpcplus_offsetX"),
                compound.getInt("cnpcplus_offsetY"),
                true
        );
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplusWriteOffset(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag == null) return;
        tag.putBoolean("CNPCPlusShapeless", cnpcplus$shapeless);
        if (!this.cnpcplusHasOffset) return;
        tag.putInt("cnpcplus_offsetX", this.cnpcplusOffsetX);
        tag.putInt("cnpcplus_offsetY", this.cnpcplusOffsetY);
    }

    @Inject(method = "copy", at = @At("RETURN"), remap = false)
    private void cnpcplusCopyOffset(RecipeCarpentry from, CallbackInfo ci) {
        RecipeCarpentryOffsetAccessor src = (RecipeCarpentryOffsetAccessor) from;
        cnpcplus$shapeless = src.cnpcplus$isShapeless();
        this.cnpcplusSetOffset(src.cnpcplusGetOffsetX(), src.cnpcplusGetOffsetY(), src.cnpcplusHasSavedOffset());
        // also preserve name - official copy() skips name
        RecipeCarpentry self = (RecipeCarpentry) (Object) this;
        if ((self.name == null || self.name.isEmpty()) && from.name != null) {
            self.name = from.name;
        }
    }
}
