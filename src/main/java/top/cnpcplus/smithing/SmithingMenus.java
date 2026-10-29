package top.cnpcplus.smithing;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 锻造配方编辑容器的 MenuType 注册（226 从 SmeltingMenus 复制）。
 * 用 IForgeMenuType.create 让服务端 NetworkHooks.openScreen 能附带选中配方 id，客户端由 buf 构造同一容器。
 * 226 本步只注册 MenuType（打开界面用），数据网络通道后续阶段按需求再建。
 */
public class SmithingMenus {
    public static MenuType<ContainerSmithingRecipes> TYPE;

    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.MENU.key(), helper -> {
            TYPE = new MenuType<>((IContainerFactory<ContainerSmithingRecipes>) ContainerSmithingRecipes::new,
                    FeatureFlags.VANILLA_SET);
            helper.register(ResourceLocation.fromNamespaceAndPath("cnpcplus", "smithing_recipes"), TYPE);
        });
    }
}
