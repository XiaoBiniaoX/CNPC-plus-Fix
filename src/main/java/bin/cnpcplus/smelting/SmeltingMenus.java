package bin.cnpcplus.smelting;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.network.IContainerFactory;

public final class SmeltingMenus {
    public static MenuType<ContainerSmeltingRecipes> TYPE;
    private SmeltingMenus() {}
    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.MENU.key(), helper -> {
            TYPE = new MenuType<>((IContainerFactory<ContainerSmeltingRecipes>) ContainerSmeltingRecipes::new, net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
            helper.register(ResourceLocation.fromNamespaceAndPath("cnpcplus", "smelting_recipes"), TYPE);
        });
    }
}
