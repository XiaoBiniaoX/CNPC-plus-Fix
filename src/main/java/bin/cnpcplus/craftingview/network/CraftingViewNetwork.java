package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import bin.cnpcplus.recipe.network.PacketRecipePersist;
import bin.cnpcplus.trader.network.PacketTraderPage;
import bin.cnpcplus.trader.network.PacketTraderPageSync;
import bin.cnpcplus.follower.network.PacketFollowerDismiss;
import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import bin.cnpcplus.smelting.network.PacketSmeltingSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CraftingViewNetwork {
    private CraftingViewNetwork() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CraftingViewNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        // optional() 是服务端场景的关键：本模组的包全部只服务于自身的编辑界面，
        // 不是进入世界的必要条件。若不标 optional，NeoForge 在频道协商阶段
        // 会因为对端缺少该频道或版本不一致而直接判定不兼容并拒绝连接
        // （NetworkComponentNegotiator 的 failure.missing.server.client /
        //   failure.version.mismatch 分支），玩家侧就表现为进不去游戏。
        // 标 optional 后，协商失败只会把这些频道降级为禁用（buildDisabledOptionalComponents），
        // 玩家仍可正常进服，只是用不到本模组的对应界面。
        PayloadRegistrar registrar = event.registrar(CnpcPlus.MODID).versioned("1").optional();
        registrar.playToServer(
                PacketFillCraftingGrid.TYPE,
                PacketFillCraftingGrid.STREAM_CODEC,
                PacketFillCraftingGrid::handle
        );
        registrar.playToServer(
                PacketRecipePersist.TYPE,
                PacketRecipePersist.STREAM_CODEC,
                PacketRecipePersist::handle
        );
        registrar.playToServer(
                PacketTraderPage.TYPE,
                PacketTraderPage.STREAM_CODEC,
                PacketTraderPage::handle
        );
        registrar.playToServer(
                PacketNpcInvPage.TYPE,
                PacketNpcInvPage.STREAM_CODEC,
                PacketNpcInvPage::handle
        );
        registrar.playToServer(
                PacketFollowerDismiss.TYPE,
                PacketFollowerDismiss.STREAM_CODEC,
                PacketFollowerDismiss::handle
        );
        registrar.playToClient(
                PacketTraderPageSync.TYPE,
                PacketTraderPageSync.STREAM_CODEC,
                PacketTraderPageSync::handle
        );
        registrar.playToServer(
                PacketSmeltingAction.TYPE,
                PacketSmeltingAction.STREAM_CODEC,
                PacketSmeltingAction::handle
        );
        registrar.playToClient(
                PacketSmeltingSync.TYPE,
                PacketSmeltingSync.STREAM_CODEC,
                PacketSmeltingSync::handle
        );
    }
}
