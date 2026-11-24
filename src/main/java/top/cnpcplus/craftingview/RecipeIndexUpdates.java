package top.cnpcplus.craftingview;

import com.google.common.collect.LinkedHashMultimap;
import java.util.LinkedHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import top.cnpcplus.mixin.RecipeManagerAccess;

public final class RecipeIndexUpdates {
    private RecipeIndexUpdates() {}
    /** Both callers run on their logical main thread. Convert immutable reload indexes once. */
    public static void replace(RecipeManager manager, ResourceLocation id, RecipeHolder<?> recipe) {
        var access = (RecipeManagerAccess)manager;
        if (!(access.cnpcplus$getByType() instanceof LinkedHashMultimap))
            access.cnpcplus$setByType(LinkedHashMultimap.create(access.cnpcplus$getByType()));
        if (!(access.cnpcplus$getByName() instanceof LinkedHashMap))
            access.cnpcplus$setByName(new LinkedHashMap<>(access.cnpcplus$getByName()));
        var old = access.cnpcplus$getByName().remove(id);
        if (old != null) access.cnpcplus$getByType().remove(old.value().getType(), old);
        if (recipe != null) {
            access.cnpcplus$getByType().put(recipe.value().getType(), recipe);
            access.cnpcplus$getByName().put(id, recipe);
        }
    }
}
