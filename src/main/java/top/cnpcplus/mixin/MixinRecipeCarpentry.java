package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.accessor.RecipeCarpentryOffsetAccessor;
import top.cnpcplus.craftingview.CraftUtils;

@Mixin(RecipeCarpentry.class)
public abstract class MixinRecipeCarpentry implements RecipeCarpentryOffsetAccessor {

    @Unique
    private int cnpcplus$offsetX;

    @Unique
    private int cnpcplus$offsetY;

    @Unique
    private boolean cnpcplus$hasSavedOffset;

    @Shadow(remap = false)
    public boolean ignoreDamage;

    @Shadow(remap = false)
    public boolean ignoreNBT;

    @Shadow(remap = false)
    public abstract ItemStack getCraftingItem(int i);

    @Override
    public int cnpcplus$getOffsetX() {
        return this.cnpcplus$offsetX;
    }

    @Override
    public int cnpcplus$getOffsetY() {
        return this.cnpcplus$offsetY;
    }

    @Override
    public boolean cnpcplus$hasSavedOffset() {
        return this.cnpcplus$hasSavedOffset;
    }

    @Override
    public void cnpcplus$setOffset(int offsetX, int offsetY, boolean savedOffset) {
        this.cnpcplus$offsetX = offsetX;
        this.cnpcplus$offsetY = offsetY;
        this.cnpcplus$hasSavedOffset = savedOffset;
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void cnpcplus$readOffset(CompoundTag compound, CallbackInfoReturnable<RecipeCarpentry> cir) {
        if (!compound.contains("cnpcplus_offsetX", 3) && !compound.contains("cnpcplus_offsetY", 3)) return;
        ((RecipeCarpentryOffsetAccessor) cir.getReturnValue()).cnpcplus$setOffset(
                compound.getInt("cnpcplus_offsetX"),
                compound.getInt("cnpcplus_offsetY"),
                true
        );
    }

    @Inject(method = "writeNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeOffset(CallbackInfoReturnable<CompoundTag> cir) {
        if (!this.cnpcplus$hasSavedOffset) return;
        CompoundTag compound = cir.getReturnValue();
        compound.putInt("cnpcplus_offsetX", this.cnpcplus$offsetX);
        compound.putInt("cnpcplus_offsetY", this.cnpcplus$offsetY);
    }

    @Inject(method = "copy", at = @At("RETURN"), remap = false)
    private void cnpcplus$copyOffset(RecipeCarpentry recipe, CallbackInfo ci) {
        RecipeCarpentryOffsetAccessor from = (RecipeCarpentryOffsetAccessor) recipe;
        this.cnpcplus$setOffset(from.cnpcplus$getOffsetX(), from.cnpcplus$getOffsetY(), from.cnpcplus$hasSavedOffset());
    }

    @Inject(method = "m_5818_", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$matchSavedOffset(CraftingContainer inventoryCrafting, Level world, CallbackInfoReturnable<Boolean> cir) {
        if (this.cnpcplus$hasSavedOffset && (this.cnpcplus$offsetX != 0 || this.cnpcplus$offsetY != 0)) {
            boolean normal = this.cnpcplus$checkMatch(inventoryCrafting, this.cnpcplus$offsetX, this.cnpcplus$offsetY, false);
            boolean mirrored = this.cnpcplus$checkMatch(inventoryCrafting, this.cnpcplus$offsetX, this.cnpcplus$offsetY, true);
            cir.setReturnValue(normal || mirrored);
            return;
        }

        if (!this.ignoreDamage && !this.ignoreNBT) return;
        RecipeCarpentry recipe = (RecipeCarpentry) (Object) this;
        int gridWidth = inventoryCrafting.getWidth();
        int gridHeight = inventoryCrafting.getHeight();
        for (int x = 0; x <= gridWidth - recipe.getWidth(); x++) {
            for (int y = 0; y <= gridHeight - recipe.getHeight(); y++) {
                if (this.cnpcplus$checkMatch(inventoryCrafting, x, y, false)
                        || this.cnpcplus$checkMatch(inventoryCrafting, x, y, true)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
        cir.setReturnValue(false);
    }

    @Unique
    private boolean cnpcplus$checkMatch(Container inventoryCrafting, int regionX, int regionY, boolean mirrored) {
        RecipeCarpentry recipe = (RecipeCarpentry) (Object) this;
        int gridWidth = inventoryCrafting instanceof CraftingContainer crafting ? crafting.getWidth() : 4;
        int gridHeight = inventoryCrafting instanceof CraftingContainer crafting ? crafting.getHeight() : 4;
        for (int row = 0; row < gridHeight; row++) {
            for (int col = 0; col < gridWidth; col++) {
                int recipeX = col - regionX;
                int recipeY = row - regionY;
                ItemStack required = ItemStack.EMPTY;
                if (recipeX >= 0 && recipeY >= 0 && recipeX < recipe.getWidth() && recipeY < recipe.getHeight()) {
                    int x = mirrored ? recipe.getWidth() - recipeX - 1 : recipeX;
                    required = this.getCraftingItem(x + recipeY * recipe.getWidth());
                }

                ItemStack actual = inventoryCrafting.getItem(col + row * gridWidth);
                if (actual.isEmpty() && required.isEmpty()) continue;
                if (actual.isEmpty() || required.isEmpty()) return false;
                if (!CraftUtils.matches(actual, required, this.ignoreDamage, this.ignoreNBT)) return false;
            }
        }
        return true;
    }
}
