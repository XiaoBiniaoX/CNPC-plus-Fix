package top.cnpcplus.craftingview;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.controllers.data.RecipeCarpentry;
import top.cnpcplus.accessor.RecipeCarpentryOffsetAccessor;

public class RecipeView {

    public final RecipeCarpentry delegate;
    public final ResourceLocation id;
    public final String name;
    public final int recipeWidth;
    public final int recipeHeight;
    public final boolean ignoreDamage;
    public final boolean ignoreNBT;
    public final boolean global;
    public final int offsetX;
    public final int offsetY;

    private ItemStack cachedOutput;
    private String cachedLowerCaseName;
    private String cachedLowerCaseDisplayName;
    private boolean outputCached = false;

    public RecipeView(RecipeCarpentry delegate, ResourceLocation id, boolean global) {
        this.delegate = delegate;
        this.id = id;
        this.global = global;
        this.name = delegate.name;
        this.recipeWidth = delegate.getWidth();
        this.recipeHeight = delegate.getHeight();
        this.ignoreDamage = delegate.ignoreDamage;
        this.ignoreNBT = delegate.ignoreNBT;
        RecipeCarpentryOffsetAccessor offset = (RecipeCarpentryOffsetAccessor) delegate;
        this.offsetX = offset.cnpcplus$getOffsetX();
        this.offsetY = offset.cnpcplus$getOffsetY();
    }

    public ItemStack getRecipeOutput() {
        if (!outputCached) {
            cachedOutput = delegate.getResult();
            outputCached = true;
        }
        return cachedOutput;
    }

    public ItemStack getCraftingItem(int index) {
        return delegate.getCraftingItem(index);
    }

    public String getLowerCaseName() {
        if (cachedLowerCaseName == null && name != null) {
            cachedLowerCaseName = name.toLowerCase();
        }
        return cachedLowerCaseName;
    }

    public String getLowerCaseDisplayName() {
        if (cachedLowerCaseDisplayName == null) {
            ItemStack output = getRecipeOutput();
            if (output != null) {
                cachedLowerCaseDisplayName = output.getHoverName().getString().toLowerCase();
            }
        }
        return cachedLowerCaseDisplayName;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RecipeView && ((RecipeView) o).delegate == this.delegate;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(delegate);
    }
}
