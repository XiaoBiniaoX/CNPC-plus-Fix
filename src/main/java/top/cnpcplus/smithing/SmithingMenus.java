package top.cnpcplus.smithing;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.cnpcplus.CnpcPlus;

/**
 * 锻造配方编辑容器的 MenuType 注册（226 从 SmeltingMenus 复制）。
 * 用 IForgeMenuType.create 让服务端 NetworkHooks.openScreen 能附带选中配方 id，客户端由 buf 构造同一容器。
 * 226 本步只注册 MenuType（打开界面用），数据网络通道后续阶段按需求再建。
 */
public class SmithingMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CnpcPlus.MOD_ID);

    public static final RegistryObject<MenuType<ContainerSmithingRecipes>> SMITHING_RECIPES =
            MENUS.register("smithing_recipes", () -> IForgeMenuType.create(ContainerSmithingRecipes::new));

    public static void register() {
        MENUS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
    }
}
