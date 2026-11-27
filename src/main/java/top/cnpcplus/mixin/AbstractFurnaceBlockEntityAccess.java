package top.cnpcplus.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractFurnaceBlockEntity.class, remap = false)
public interface AbstractFurnaceBlockEntityAccess {
    @Accessor("recipesUsed") Object2IntOpenHashMap<ResourceLocation> cnpcplus$getRecipesUsed();
}
