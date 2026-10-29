package bin.cnpcplus.craftingview.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import bin.cnpcplus.linked.network.PacketLinkedScriptSync;
import bin.cnpcplus.linked.network.PacketLinkedScriptSyncQuery;
import bin.cnpcplus.linked.network.PacketLinkedScriptSyncState;
import bin.cnpcplus.trader.network.PacketTraderPage;
import bin.cnpcplus.trader.network.PacketTraderPageSync;
import bin.cnpcplus.follower.network.PacketFollowerDismiss;
import bin.cnpcplus.smelting.network.PacketSmeltingAction;
import bin.cnpcplus.smelting.network.PacketSmeltingSync;
import top.cnpcplus.anvil.network.PacketAnvilRemove;
import top.cnpcplus.anvil.network.PacketAnvilRequestList;
import top.cnpcplus.anvil.network.PacketAnvilSave;
import top.cnpcplus.anvil.network.PacketAnvilSelect;
import top.cnpcplus.anvil.network.PacketAnvilSync;
import top.cnpcplus.anvil.network.PacketOpenAnvilGui;
import top.cnpcplus.smithing.network.PacketOpenSmithingGui;
import top.cnpcplus.smithing.network.PacketSmithingSave;
import top.cnpcplus.smithing.network.PacketSmithingRemove;
import top.cnpcplus.smithing.network.PacketSmithingSync;
import top.cnpcplus.smithing.network.PacketSmithingRequestList;
import top.cnpcplus.smithing.network.PacketSmithingSelect;
import top.cnpcplus.drop.network.PacketOpenDropGui;
import top.cnpcplus.drop.network.PacketDropSave;
import top.cnpcplus.drop.network.PacketDropRemove;
import top.cnpcplus.drop.network.PacketDropSync;
import top.cnpcplus.drop.network.PacketDropRequestList;
import top.cnpcplus.drop.network.PacketDropSelect;
import top.cnpcplus.transport.network.PacketTransportMetaRequest;
import top.cnpcplus.transport.network.PacketTransportMetaResponse;
import top.cnpcplus.transport.network.PacketTransportMetaSave;
import top.cnpcplus.transport.network.PacketTransportImageRequest;
import top.cnpcplus.transport.network.PacketTransportImageResponse;
import top.cnpcplus.transport.network.PacketTransportImageUpload;
import top.cnpcplus.transport.network.PacketTransportImageChunk;
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
        registrar.playToServer(PacketOpenAnvilGui.TYPE, PacketOpenAnvilGui.STREAM_CODEC, PacketOpenAnvilGui::handle);
        registrar.playToServer(PacketAnvilSave.TYPE, PacketAnvilSave.STREAM_CODEC, PacketAnvilSave::handle);
        registrar.playToServer(PacketAnvilRemove.TYPE, PacketAnvilRemove.STREAM_CODEC, PacketAnvilRemove::handle);
        registrar.playToClient(PacketAnvilSync.TYPE, PacketAnvilSync.STREAM_CODEC, PacketAnvilSync::handle);
        registrar.playToServer(PacketAnvilRequestList.TYPE, PacketAnvilRequestList.STREAM_CODEC, PacketAnvilRequestList::handle);
        registrar.playToServer(PacketAnvilSelect.TYPE, PacketAnvilSelect.STREAM_CODEC, PacketAnvilSelect::handle);
        registrar.playToServer(PacketOpenSmithingGui.TYPE, PacketOpenSmithingGui.STREAM_CODEC, PacketOpenSmithingGui::handle);
        registrar.playToServer(PacketSmithingSave.TYPE, PacketSmithingSave.STREAM_CODEC, PacketSmithingSave::handle);
        registrar.playToServer(PacketSmithingRemove.TYPE, PacketSmithingRemove.STREAM_CODEC, PacketSmithingRemove::handle);
        registrar.playToClient(PacketSmithingSync.TYPE, PacketSmithingSync.STREAM_CODEC, PacketSmithingSync::handle);
        registrar.playToServer(PacketSmithingRequestList.TYPE, PacketSmithingRequestList.STREAM_CODEC, PacketSmithingRequestList::handle);
        registrar.playToServer(PacketSmithingSelect.TYPE, PacketSmithingSelect.STREAM_CODEC, PacketSmithingSelect::handle);
        registrar.playToServer(PacketOpenDropGui.TYPE, PacketOpenDropGui.STREAM_CODEC, PacketOpenDropGui::handle);
        registrar.playToServer(PacketDropSave.TYPE, PacketDropSave.STREAM_CODEC, PacketDropSave::handle);
        registrar.playToServer(PacketDropRemove.TYPE, PacketDropRemove.STREAM_CODEC, PacketDropRemove::handle);
        registrar.playToClient(PacketDropSync.TYPE, PacketDropSync.STREAM_CODEC, PacketDropSync::handle);
        registrar.playToServer(PacketDropRequestList.TYPE, PacketDropRequestList.STREAM_CODEC, PacketDropRequestList::handle);
        registrar.playToServer(PacketDropSelect.TYPE, PacketDropSelect.STREAM_CODEC, PacketDropSelect::handle);
        registrar.playToServer(PacketTransportMetaRequest.TYPE, PacketTransportMetaRequest.STREAM_CODEC, (packet, context) -> packet.handle(context));
        registrar.playToClient(PacketTransportMetaResponse.TYPE, PacketTransportMetaResponse.STREAM_CODEC, (packet, context) -> packet.handle(context));
        registrar.playToServer(PacketTransportMetaSave.TYPE, PacketTransportMetaSave.STREAM_CODEC, (packet, context) -> packet.handle(context));
        registrar.playToServer(PacketTransportImageRequest.TYPE, PacketTransportImageRequest.STREAM_CODEC, (packet, context) -> packet.handle(context));
        registrar.playToClient(PacketTransportImageResponse.TYPE, PacketTransportImageResponse.STREAM_CODEC, (packet, context) -> packet.handle(context));
        registrar.playToServer(PacketTransportImageUpload.TYPE, PacketTransportImageUpload.STREAM_CODEC, (packet, context) -> packet.handle(context));
        registrar.playToServer(PacketTransportImageChunk.TYPE, PacketTransportImageChunk.STREAM_CODEC, (packet, context) -> packet.handle(context));
        registrar.playToServer(top.cnpcplus.villagertrade.network.PacketEditInventoryCommit.TYPE,
                top.cnpcplus.villagertrade.network.PacketEditInventoryCommit.STREAM_CODEC,
                top.cnpcplus.villagertrade.network.PacketEditInventoryCommit::handle);
        registrar.playToServer(
                PacketLinkedScriptSync.TYPE,
                PacketLinkedScriptSync.STREAM_CODEC,
                PacketLinkedScriptSync::handle
        );
        registrar.playToServer(
                PacketLinkedScriptSyncQuery.TYPE,
                PacketLinkedScriptSyncQuery.STREAM_CODEC,
                PacketLinkedScriptSyncQuery::handle
        );
        registrar.playToClient(
                PacketLinkedScriptSyncState.TYPE,
                PacketLinkedScriptSyncState.STREAM_CODEC,
                PacketLinkedScriptSyncState::handle
        );
    }
}
