package top.cnpcplus.smelting;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.cnpcplus.CnpcPlus;

/**
 * 熔炼配方编辑容器的 MenuType 注册。
 * 用 IForgeMenuType.create 是为了让服务端能通过 NetworkHooks.openScreen 附带额外数据（选中的配方 id），
 * 客户端再由 buf 构造同一个容器 —— 也就是说 ContainerSmeltingRecipes 的构造函数两端都会执行。
 */
public class SmeltingMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, CnpcPlus.MOD_ID);

    public static final RegistryObject<MenuType<ContainerSmeltingRecipes>> SMELTING_RECIPES =
            MENUS.register("smelting_recipes", () -> IForgeMenuType.create(ContainerSmeltingRecipes::new));

    public static void register() {
        MENUS.register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
    }
}
