package top.cnpcplus.anvil;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 铁砧配方编辑容器的 MenuType 注册（阶段231 从 SmithingMenus 复制）。
 * 用 IForgeMenuType.create 让服务端 NetworkHooks.openScreen 能附带选中配方 id，客户端由 buf 构造同一容器。
 */
public final class AnvilMenus {
    public static MenuType<ContainerAnvilRecipes> TYPE;

    private AnvilMenus() {}

    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.MENU.key(), helper -> {
            TYPE = new MenuType<>((IContainerFactory<ContainerAnvilRecipes>) ContainerAnvilRecipes::new,
                    net.minecraft.world.flag.FeatureFlags.VANILLA_SET);
            helper.register(ResourceLocation.fromNamespaceAndPath("cnpcplus", "anvil_recipes"), TYPE);
        });
    }
}
