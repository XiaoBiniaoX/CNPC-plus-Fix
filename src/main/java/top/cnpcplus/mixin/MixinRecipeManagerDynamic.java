package top.cnpcplus.mixin;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.smelting.SmeltingRecipeManager;

import java.util.Map;

/**
 * B3: RecipeManager reload（apply）后把自定义熔炼配方注入。
 * 覆盖游戏重载 / 世界重新进入 / /reload 场景 —— 每次 apply 后重新注入全部自定义配方。
 */
@Mixin(RecipeManager.class)
public class MixinRecipeManagerDynamic {

    @Inject(method = "apply", at = @At("TAIL"))
    private void cnpcplus$injectCustomSmelting(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profilerFiller, CallbackInfo ci) {
        // 无条件注入。这里曾用 ServerLifecycleHooks.getCurrentServer()==null 做守卫来阻止客户端读盘，
        // 但世界加载时首次 apply 发生在 MinecraftServer 实例创建之前，getCurrentServer() 那时仍为 null，
        // 导致开服后自定义配方完全不注册（必须 /reload 才生效）。
        // 客户端不读盘的问题改由 SmeltingRecipeRegistry 自己把关（无服务端实例时返回空表）。
        SmeltingRecipeManager.injectAll((RecipeManager) (Object) this);
    }
}
