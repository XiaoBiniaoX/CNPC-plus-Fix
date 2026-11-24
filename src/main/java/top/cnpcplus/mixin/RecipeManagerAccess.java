package top.cnpcplus.mixin;

import com.google.common.collect.Multimap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeManager.class)
public interface RecipeManagerAccess {
    @Accessor("byType") Multimap<RecipeType<?>, RecipeHolder<?>> cnpcplus$getByType();
    @Accessor("byType") void cnpcplus$setByType(Multimap<RecipeType<?>, RecipeHolder<?>> value);
    @Accessor("byName") Map<ResourceLocation, RecipeHolder<?>> cnpcplus$getByName();
    @Accessor("byName") void cnpcplus$setByName(Map<ResourceLocation, RecipeHolder<?>> value);
}
