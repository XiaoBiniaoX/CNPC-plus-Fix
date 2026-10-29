package top.cnpcplus.smithing.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.smithing.SmithingRecipeData;

import java.util.List;

/**
 * 配方同步包在客户端的处理入口（227 从 SmeltingClientSync 复制）。
 * 独立成 client-only 类，是为了让 PacketSmithingSync（common 包，服务端也会加载）
 * 不直接引用 Minecraft/GUI 类型，避免专用服务器解析到客户端类。
 */
public final class SmithingClientSync {

    private SmithingClientSync() {}

    public static void accept(List<SmithingRecipeData> data) {
        SmithingClientData.set(data);
        net.minecraft.client.Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuiNpcSmithingRecipes gui) {
            gui.refreshFromServer();
        }
    }
}
