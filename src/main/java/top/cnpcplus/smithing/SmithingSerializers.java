package top.cnpcplus.smithing;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import bin.cnpcplus.CnpcPlus;

/**
 * 锻造配方序列化器注册（227）。
 * 必须进 Forge 配方序列化器注册表：服务端把配方下发给客户端时（原版配方同步包），
 * RecipeManager.toNetwork 要用注册表 key 反查序列化器，客户端按同一 key 取出做 fromNetwork
 * ——没注册 = 双方都拿不到 key（NPE/空指针），一进存档就炸。
 * 注册时机与 SmithingMenus 同惯例：mod 构造期挂到 MOD 总线，RegisterEvent 阶段生效，
 * 早于任何 RecipeManager.apply 与配方同步。
 */
public class SmithingSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CnpcPlus.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, CustomSmithingRecipe.Serializer> SMITHING_TRANSFORM =
            SERIALIZERS.register("smithing_transform", CustomSmithingRecipe.Serializer::new);

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
