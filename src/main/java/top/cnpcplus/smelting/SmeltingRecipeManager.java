package top.cnpcplus.smelting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.network.PacketDistributor;
import top.cnpcplus.mixin.RecipeManagerAccess;
import top.cnpcplus.smelting.network.PacketSmeltingSync;
import top.cnpcplus.smelting.network.SmeltingPacketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自定义熔炼配方生命周期管理：负责把独立 Registry(SmeltingRecipeRegistry) 的配方
 * 动态注册/同步进 Minecraft RecipeManager（RecipeManager 只是最终呈现层），并广播给客户端。
 */
public final class SmeltingRecipeManager {

    private SmeltingRecipeManager() {}

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
        List<String> prefixes = List.of("cnpcplus:smelting/", "cnpcplus:blasting/", "cnpcplus:smoking/");
        for (var it = byName.entrySet().iterator(); it.hasNext(); ) {
            String id = it.next().getKey().toString();
            boolean ours = false;
            for (String p : prefixes) if (id.startsWith(p)) { ours = true; break; }
            if (ours) it.remove();
        }
        for (var typeEntry : recipes.values()) {
            typeEntry.keySet().removeIf(id -> {
                String s = id.toString();
                for (String p : prefixes) if (s.startsWith(p)) return true;
                return false;
            });
        }
        // 注入全部自定义配方
        List<SmeltingRecipeData> all = SmeltingRecipeRegistry.list();
        for (SmeltingRecipeData data : all) {
            for (AbstractCookingRecipe r : SmeltingRecipeParser.parse(data)) {
                recipes.computeIfAbsent(r.getType(), k -> new HashMap<>()).put(r.getId(), r);
                byName.put(r.getId(), r);
            }
        }
        ((RecipeManagerAccess) manager).cnpcplus$setRecipes(recipes);
        ((RecipeManagerAccess) manager).cnpcplus$setByName(byName);
    }

    /** 把配方列表同步给全部在线玩家（编辑界面用）。 */
    public static void syncToPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            syncToPlayers(server, p);
        }
    }

    public static void syncToPlayers(MinecraftServer server, ServerPlayer target) {
        if (target == null || server == null) return;
        List<SmeltingRecipeData> list = SmeltingRecipeRegistry.list();
        SmeltingPacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new PacketSmeltingSync(list));
    }

    /**
     * 重新下发原版配方表给所有客户端。
     * 上面的 syncToPlayers 只更新我们自己的编辑界面列表；客户端的 RecipeManager（配方书、JEI 等）
     * 要靠原版的配方同步包才会更新，否则改完配方后客户端一直是旧数据，直到重连。
     */
    public static void resendVanillaRecipes(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(
                    server.getRecipeManager().getRecipes()));
        }
    }
}
