package top.cnpcplus.bossbar.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.cnpcplus.bossbar.BossBarPacket;

@Mod.EventBusSubscriber(modid = "cnpcplus", value = Dist.CLIENT)
public final class BossBarColors {
    /** Vanilla boss bar sheet; every {@link BossEvent.BossBarColor} owns ten rows (frame + fill). */
    private static final ResourceLocation GUI_BARS = new ResourceLocation("textures/gui/bars.png");
    /** WHITE is the only untinted pair, so the RGB tint below stays faithful. */
    private static final int WHITE_ROW = BossEvent.BossBarColor.WHITE.ordinal() * 10;
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

    /** Same geometry as vanilla {@code BossHealthOverlay}: 182x5 frame, fill = progress * 183. */
    public static void bar(GuiGraphics g, int x, int y, int rgb, float progress) {
        int width = (int) (Mth.clamp(progress, 0.0F, 1.0F) * 183.0F);
        RenderSystem.setShaderColor(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F,
                1.0F);
        g.blit(GUI_BARS, x, y, 0, WHITE_ROW, 182, 5);
        if (width > 0) g.blit(GUI_BARS, x, y, 0, WHITE_ROW + 5, width, 5);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
