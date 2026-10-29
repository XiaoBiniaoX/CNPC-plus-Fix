package top.cnpcplus.smithing;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.network.PacketDistributor;
import top.cnpcplus.smithing.network.PacketSmithingSync;

import java.util.ArrayList;
import java.util.List;

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
    private static final String PREFIX = "smithing/";

    /**
     * 把 Registry 全部配方（重新）注入给定的 RecipeManager。reload 后也调用它。
     * 客户端的 RecipeManager 由服务端下发的配方包填充，不在这里注入（否则连服时会用上本地文件的配方，
     * 与服务端不一致）；判定放在 Registry.list()，客户端拿到空表后本方法只做一次清理，等价于不注入。
     */
    public static void injectAll(RecipeManager manager) {
        if (manager == null) return;
        List<RecipeHolder<?>> recipes = new ArrayList<>(manager.getRecipes());
        recipes.removeIf(r -> r.id().getNamespace().equals("cnpcplus") && r.id().getPath().startsWith(PREFIX));
        // 仅清理本模块，保留原版、全局合成和熔炼配方。
        for (SmithingRecipeData data : SmithingRecipeRegistry.list()) {
            RecipeHolder<CustomSmithingRecipe> r = SmithingRecipeParser.parse(data);
            if (r == null) continue;
            recipes.add(r);
        }
        manager.replaceRecipes(recipes);
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
        PacketDistributor.sendToPlayer(target, new PacketSmithingSync(list));
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
        java.util.Collection<RecipeHolder<?>> all = server.getRecipeManager().getRecipes();
        var players = server.getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            if (p.containerMenu instanceof SmithingMenuAccess menu) {
                menu.cnpcplus$refreshRecipes();
                p.containerMenu.broadcastChanges();
            }
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket(all));
        }
    }
}
