package top.cnpcplus.drop.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.drop.DropRecipeData;

import java.util.List;

/**
 * 配方同步包在客户端的处理入口（阶段232 从 AnvilClientSync 复制）。
 * 独立成 client-only 类，是为了让 PacketDropSync（common 包，服务端也会加载）
 * 不直接引用 Minecraft/GUI 类型，避免专用服务器解析到客户端类。
 */
public final class DropClientSync {

    private DropClientSync() {}

    public static void accept(List<DropRecipeData> data) {
        DropClientData.set(data);
        net.minecraft.client.Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuiDropRecipes gui) {
            gui.refreshFromServer();
        }
    }
}
