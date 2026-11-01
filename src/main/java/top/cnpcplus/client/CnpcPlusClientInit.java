package top.cnpcplus.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

@OnlyIn(Dist.CLIENT)
public final class CnpcPlusClientInit {
    private CnpcPlusClientInit() {
    }

    public static void init() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                top.cnpcplus.config.CnpcPlusConfigData.getConfig(), "cnpcplus.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                top.cnpcplus.perf.Config.SPEC, "cnpcplus-perf.toml");
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parent) -> new top.cnpcplus.config.client.CnpcPlusConfigScreen(parent)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent ce) -> {
            if (ce.phase == TickEvent.Phase.END) {
                top.cnpcplus.sync.client.PendingNpcUpdateStore.tick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(top.cnpcplus.wand.client.WandHitboxOverlay::render);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) -> {
            if (!event.getLevel().isClientSide || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                    || event.getItemStack().getItem() != noppes.npcs.CustomItems.cloner) return;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (event.getEntity() != mc.player || mc.screen != null) return;
            noppes.npcs.CustomNpcs.proxy.openGui(event.getEntity(), noppes.npcs.constants.EnumGuiType.NpcRemote);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            event.setCanceled(true);
        });
    }
}
