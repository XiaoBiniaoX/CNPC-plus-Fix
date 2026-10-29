package top.cnpcplus.smithing;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.network.PacketDistributor;
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
 * <p>热添加路径：保存/删除 → injectAll（原版锻造台每次操作都从 RecipeManager 现查配方，
 * 注册表更新后**下一次点击立即生效**，无需重进世界）→ syncToPlayers（编辑界面列表，全员）→
 * resendVanillaRecipes（原版配方表，**全员**——228 用户要求「一个人修改，全部玩家都有效果」，
 * 群发本身是 netty 出站排队非阻塞，编码在 IO 线程，主线程只做轻量 packet 构造）。
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

    /** 把配方列表同步给全部在线玩家（编辑界面用）。 */
    public static void syncToPlayers(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            syncToPlayers(server, p);
        }
    }

    public static void syncToPlayers(MinecraftServer server, ServerPlayer target) {
        if (target == null || server == null) return;
        List<SmithingRecipeData> list = SmithingRecipeRegistry.list();
        SmithingPacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new PacketSmithingSync(list));
    }

    /**
     * 重新下发原版配方表给**全部在线玩家**（228 用户要求：一人修改，全员立即可合成）。
     * 上面的 syncToPlayers 只更新我们自己的编辑界面列表；客户端的 RecipeManager（配方书、JEI、
     * 槽位准入等）要靠原版的配方同步包才会更新，否则其他玩家改完后一直是旧数据，直到重连。
     *
     * <p>群发不再怕卡：packet 构造只持配方引用列表（轻量），真正的 toNetwork 编码发生在 Netty
     * IO 线程（原版同款路径），主线程没有序列化阻塞。
     */
    public static void resendVanillaRecipes(MinecraftServer server) {
        if (server == null) return;
        java.util.Collection<Recipe<?>> all = server.getRecipeManager().getRecipes();
        var players = server.getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(all));
        }
    }
}
