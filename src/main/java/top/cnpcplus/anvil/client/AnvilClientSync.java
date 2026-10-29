package top.cnpcplus.anvil.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.anvil.AnvilRecipeData;

import java.util.List;

/**
 * 配方同步包在客户端的处理入口（231 从 SmithingClientSync 复制）。
 * 独立成 client-only 类，是为了让 PacketAnvilSync（common 包，服务端也会加载）
 * 不直接引用 Minecraft/GUI 类型，避免专用服务器解析到客户端类。
 */
public final class AnvilClientSync {

    private AnvilClientSync() {}

    public static void accept(List<AnvilRecipeData> data) {
        AnvilClientData.set(data);
        net.minecraft.client.Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuiNpcAnvilRecipes gui) {
            gui.refreshFromServer();
        }
    }
}
