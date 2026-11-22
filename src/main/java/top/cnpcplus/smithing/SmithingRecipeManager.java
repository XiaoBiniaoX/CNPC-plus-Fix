package top.cnpcplus.smithing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcplus.craftingview.RecipeIndexUpdates;
import top.cnpcplus.mixin.RecipeManagerAccess;
import top.cnpcplus.smithing.network.PacketSmithingSync;
import top.cnpcplus.smithing.network.SmithingPacketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义锻造配方生命周期管理（227 从 SmeltingRecipeManager 复制）：
 * 负责把独立 Registry(SmithingRecipeRegistry) 的配方动态注册/同步进 Minecraft RecipeManager
 * （RecipeManager 只是最终呈现层），并广播给客户端。
 *
 * <p>启动和重载时载入全部自定义锻造配方。编辑时更新已变动的条目并同步所有在线玩家。
 */
public final class SmithingRecipeManager {

    private SmithingRecipeManager() {}

    /** 本 mod 动态注入配方的 id 前缀：reload 清理与注入都按它认亲（与熔炼前缀互不干扰）。 */
    private static final List<String> PREFIXES = List.of("cnpcplus:smithing/");

    /**
     * 把 Registry 全部配方（重新）注入给定的 RecipeManager。reload 后也调用它。
     * 客户端的 RecipeManager 由服务端下发的配方包填充，不在这里注入（否则连服时会用上本地文件的配方，
     * 与服务端不一致）；判定放在 Registry.list()，客户端拿到空表后本方法只做一次清理，等价于不注入。
     */
    public static void injectAll(RecipeManager manager) {
        if (manager == null) return;
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes = new HashMap<>(
                ((RecipeManagerAccess) manager).cnpcplus$getRecipes());
        // 两层可变副本：RecipeManager 内部的内层 value 是不可变 ImmutableMap，
        // 必须先转成可变 HashMap 才能对其 removeIf（配方对象本身是共享引用，不需要也不应该拷贝）
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> e : recipes.entrySet()) {
            e.setValue(new HashMap<>(e.getValue()));
        }
        Map<ResourceLocation, Recipe<?>> byName = new HashMap<>(
                ((RecipeManagerAccess) manager).cnpcplus$getByName());
        // 先移除本 mod 已有的动态配方，避免旧条目残留
        for (var it = byName.entrySet().iterator(); it.hasNext(); ) {
            String id = it.next().getKey().toString();
            for (String p : PREFIXES) {
                if (id.startsWith(p)) { it.remove(); break; }
            }
        }
        for (var typeEntry : recipes.values()) {
            typeEntry.keySet().removeIf(id -> {
                String s = id.toString();
                for (String p : PREFIXES) if (s.startsWith(p)) return true;
                return false;
            });
        }
        // 注入全部自定义配方（锻造型挂原版 RecipeType.SMITHING 桶，原版锻造台按它现查）
        for (SmithingRecipeData data : SmithingRecipeRegistry.list()) {
            CustomSmithingRecipe r = SmithingRecipeParser.parse(data);
            if (r == null) continue;
            recipes.computeIfAbsent(r.getType(), k -> new HashMap<>()).put(r.getId(), r);
            byName.put(r.getId(), r);
        }
        ((RecipeManagerAccess) manager).cnpcplus$setRecipes(recipes);
        ((RecipeManagerAccess) manager).cnpcplus$setByName(byName);
    }

    public static void syncToPlayers(MinecraftServer server, ServerPlayer target) {
        if (target == null || server == null) return;
        List<SmithingRecipeData> list = SmithingRecipeRegistry.list();
        SmithingPacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new PacketSmithingSync(list));
    }

    public static void applyChange(RecipeManager manager, int id, SmithingRecipeData data) {
        RecipeIndexUpdates.replace(manager, RecipeType.SMITHING, SmithingRecipeParser.recipeId(id),
                SmithingRecipeParser.parse(data));
    }

    public static void publishChange(MinecraftServer server, int id, SmithingRecipeData data) {
        applyChange(server.getRecipeManager(), id, data);
        var packet = new PacketSmithingSync(data == null ? List.of() : List.of(data), id);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SmithingPacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
