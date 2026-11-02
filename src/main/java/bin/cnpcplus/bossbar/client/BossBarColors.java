package bin.cnpcplus.bossbar.client;

import bin.cnpcplus.bossbar.BossBarPacket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = "cnpcplus", value = Side.CLIENT)
public final class BossBarColors {
    private static final Map<UUID, Integer> COLORS = new HashMap<>();
    private BossBarColors() { }
    public static void receive(BossBarPacket packet) { if (packet.removed) COLORS.remove(packet.id); else COLORS.put(packet.id, packet.rgb); }
    @SubscribeEvent public static void unload(WorldEvent.Unload event) { if (event.getWorld().isRemote) COLORS.clear(); }
    public static Integer get(UUID id) { return COLORS.get(id); }
}
