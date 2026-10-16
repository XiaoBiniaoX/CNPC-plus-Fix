package top.cnpcplus.anvil;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.cnpcplus.CnpcPlus;

/**
 * 铁砧配方编辑容器的 MenuType 注册（阶段231 从 SmithingMenus 复制）。
 * 用 IForgeMenuType.create 让服务端 NetworkHooks.openScreen 能附带选中配方 id，客户端由 buf 构造同一容器。
 */
public class AnvilMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CnpcPlus.MOD_ID);

    public static final RegistryObject<MenuType<ContainerAnvilRecipes>> ANVIL_RECIPES =
            MENUS.register("anvil_recipes", () -> IForgeMenuType.create(ContainerAnvilRecipes::new));

    public static void register() {
        MENUS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
    }
}
