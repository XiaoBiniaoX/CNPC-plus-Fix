package top.cnpcplus.craftingview;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.*;
import noppes.npcs.controllers.data.RecipeCarpentry;

/** Preserve CNPC options and exact materials in the initial vanilla recipe sync and deltas. */
public final class CarpentrySerializer implements RecipeSerializer<RecipeCarpentry> {
    private final MapCodec<RecipeCarpentry> codec = new ShapedRecipe.Serializer().codec().xmap(
            shaped -> new RecipeCarpentry(shaped.getWidth(), shaped.getHeight(), shaped.getIngredients(),
                    shaped.getResultItem(net.minecraft.core.RegistryAccess.EMPTY)), recipe -> recipe);
    private final StreamCodec<RegistryFriendlyByteBuf, RecipeCarpentry> stream = StreamCodec.of(
            (buf, recipe) -> buf.writeNbt(recipe.writeNBT(buf.registryAccess())),
            buf -> RecipeCarpentry.load(buf.readNbt(), buf.registryAccess()));
    @Override public MapCodec<RecipeCarpentry> codec() { return codec; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, RecipeCarpentry> streamCodec() { return stream; }
}
