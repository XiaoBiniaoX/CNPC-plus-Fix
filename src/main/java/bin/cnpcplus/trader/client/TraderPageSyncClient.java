package bin.cnpcplus.trader.client;

import bin.cnpcplus.trader.TraderPager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.containers.ContainerNPCTraderSetup;
import noppes.npcs.roles.RoleTrader;
import noppes.npcs.shared.client.gui.components.GuiBasicContainer;

/**
 * 商人分页同步包的客户端侧处理。
 *
 * <p>为什么单独一个类：`PacketTraderPageSync` 虽然是 S2C 包，但它位于公共包，
 * 服务端在 `MixinContainerNPCTraderSync` 与 `PacketTraderPage` 里会 `new` 它来下发。
 * 如果把 `Minecraft`、`GuiBasicContainer` 直接写在包类里，专用服务端加载该类时
 * 会解析到客户端专有类型并抛 NoClassDefFoundError —— 这正是约法第 7 条第②项要防的情况。
 * 所以客户端引用全部收进本类，只在客户端实际处理包时才被加载。
 */
public final class TraderPageSyncClient {

    private TraderPageSyncClient() {
    }

    public static void apply(Player player, int page) {
        if (player == null) return;
        RoleTrader role = roleOf(player.containerMenu);
        if (role == null) return;
        TraderPager.setPageOnly(role, page);
        if (Minecraft.getInstance().screen instanceof GuiBasicContainer gui
                && gui.getMenu() == player.containerMenu) {
            gui.init();
        }
    }

    private static RoleTrader roleOf(Object menu) {
        if (menu instanceof ContainerNPCTrader c) return c.role;
        if (menu instanceof ContainerNPCTraderSetup s) return s.role;
        return null;
    }
}
