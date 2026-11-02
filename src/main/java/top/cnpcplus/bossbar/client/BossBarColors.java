package top.cnpcplus.bossbar.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import top.cnpcplus.bossbar.BossBarPacket;

@EventBusSubscriber(modid = "cnpcplus", value = Dist.CLIENT)
public final class BossBarColors {
    private static final Map<UUID, Integer> COLORS = new HashMap<>();
    private BossBarColors() {}

    public static void receive(BossBarPacket packet) {
        if (packet.removed()) COLORS.remove(packet.id());
        else COLORS.put(packet.id(), packet.rgb());
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { COLORS.clear(); }

    public static Integer color(UUID id) { return COLORS.get(id); }
}
