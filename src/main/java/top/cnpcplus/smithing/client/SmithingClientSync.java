package top.cnpcplus.smithing.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.smithing.SmithingRecipeData;
import top.cnpcplus.smithing.SmithingRecipeManager;

import java.util.List;

/**
 * 配方同步包在客户端的处理入口（227 从 SmeltingClientSync 复制）。
 * 独立成 client-only 类，是为了让 PacketSmithingSync（common 包，服务端也会加载）
 * 不直接引用 Minecraft/GUI 类型，避免专用服务器解析到客户端类。
 */
public final class SmithingClientSync {

    private SmithingClientSync() {}

    public static void accept(List<SmithingRecipeData> data, int changedId) {
        Minecraft mc = Minecraft.getInstance();
        if (changedId < 0) {
            SmithingClientData.set(data);
        } else {
            SmithingRecipeData recipe = data.isEmpty() ? null : data.get(0);
            if (SmithingClientData.isLoaded()) {
                var recipes = SmithingClientData.get();
                if (recipe == null) {
                    recipes.removeIf(entry -> entry.id == changedId);
                } else {
                    var previous = SmithingClientData.byId(changedId);
                    if (previous == null) recipes.add(recipe);
                    else recipes.set(recipes.indexOf(previous), recipe);
                }
            }
            var connection = mc.getConnection();
            if (connection != null) {
                SmithingRecipeManager.applyChange(connection.getRecipeManager(), changedId, recipe);
            }
        }
        if (mc.screen instanceof GuiNpcSmithingRecipes gui) {
            gui.refreshFromServer();
        }
    }
}
