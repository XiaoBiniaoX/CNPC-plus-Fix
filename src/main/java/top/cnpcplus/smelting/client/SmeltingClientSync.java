package top.cnpcplus.smelting.client;

import net.minecraft.client.Minecraft;
import top.cnpcplus.smelting.SmeltingRecipeData;

import java.util.List;

/**
 * 配方同步包在客户端的处理入口。
 * 独立成 client-only 类，是为了让 PacketSmeltingSync（common 包，服务端也会加载）
 * 不直接引用 Minecraft/GUI 类型，避免专用服务器解析到客户端类。
 */
public final class SmeltingClientSync {

    private SmeltingClientSync() {}

    public static void accept(List<SmeltingRecipeData> data) {
        SmeltingClientData.set(data);
        if (Minecraft.getInstance().screen instanceof GuiNpcSmeltingRecipes gui) {
            gui.refreshFromServer();
        }
    }
}
