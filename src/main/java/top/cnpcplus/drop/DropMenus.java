package top.cnpcplus.drop;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.cnpcplus.CnpcPlus;

/**
 * 掉落物配方编辑容器的 MenuType 注册（阶段232 从 AnvilMenus 复制）。
 * 用 IForgeMenuType.create 让服务端 NetworkHooks.openScreen 能附带选中配方 id，客户端由 buf 构造同一容器。
 */
public class DropMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CnpcPlus.MOD_ID);

    public static final RegistryObject<MenuType<ContainerDropRecipes>> DROP_RECIPES =
            MENUS.register("drop_recipes", () -> IForgeMenuType.create(ContainerDropRecipes::new));

    public static void register() {
        MENUS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
    }
}
