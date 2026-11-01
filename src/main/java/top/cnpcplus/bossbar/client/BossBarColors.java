package top.cnpcplus.bossbar.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.cnpcplus.bossbar.BossBarPacket;

@Mod.EventBusSubscriber(modid = "cnpcplus", value = Dist.CLIENT)
public final class BossBarColors {
    private static final Map<UUID, Integer> COLORS = new HashMap<>();
    private BossBarColors() {}

    public static void receive(BossBarPacket packet) {
        if (packet.removed()) COLORS.remove(packet.id());
        else COLORS.put(packet.id(), packet.rgb());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        COLORS.clear();
    }

    @SubscribeEvent
    public static void draw(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Integer rgb = COLORS.get(event.getBossEvent().getId());
        if (rgb == null) return; // Other mods and vanilla bosses are untouched.
        event.setCanceled(true);
        GuiGraphics graphics = event.getGuiGraphics();
        bar(graphics, event.getX(), event.getY(), rgb, event.getBossEvent().getProgress());
        var font = Minecraft.getInstance().font;
        var name = event.getBossEvent().getName();
        graphics.drawString(font, name, graphics.guiWidth() / 2 - font.width(name) / 2, event.getY() - 9, 0xFFFFFF);
    }

    public static void bar(GuiGraphics g, int x, int y, int rgb, float progress) {
        int width = Math.round(Math.max(0, Math.min(1, progress)) * 180);
        g.fill(x, y, x + 182, y + 5, 0xFF222222);
        g.fill(x + 1, y + 1, x + 181, y + 4, 0xFF555555);
        g.fill(x + 1, y + 1, x + 1 + width, y + 4, 0xFF000000 | rgb);
        g.fill(x + 1, y + 1, x + 1 + width, y + 2, 0x55FFFFFF);
    }
}
