package top.cnpcplus.drop;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 掉落物配方编辑容器的 MenuType 注册（阶段232 从 AnvilMenus 复制）。
 * 用 IForgeMenuType.create 让服务端 NetworkHooks.openScreen 能附带选中配方 id，客户端由 buf 构造同一容器。
 */
public class DropMenus {
    public static MenuType<ContainerDropRecipes> TYPE;

    public static void register(RegisterEvent event) {
        event.register(BuiltInRegistries.MENU.key(), helper -> {
            TYPE = new MenuType<>((IContainerFactory<ContainerDropRecipes>) ContainerDropRecipes::new,
                    FeatureFlags.VANILLA_SET);
            helper.register(ResourceLocation.fromNamespaceAndPath("cnpcplus", "drop_recipes"), TYPE);
        });
    }
}
