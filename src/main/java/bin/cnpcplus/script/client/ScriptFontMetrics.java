package bin.cnpcplus.script.client;

import bin.cnpcplus.script.tools.ScriptMeasure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = "cnpcplus", value = Side.CLIENT)
public final class ScriptFontMetrics {
    private ScriptFontMetrics() { }
    private static FontRenderer font() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) throw new IllegalStateException("CNPCPLUS.THREAD.CLIENT_THREAD_ONLY");
        return mc.fontRenderer;
    }
    @SubscribeEvent public static void ready(TextureStitchEvent.Post event) {
        ScriptMeasure.install(text -> font().getStringWidth(text), text -> text.split("\\n", -1).length * font().FONT_HEIGHT,
                (text, width) -> font().listFormattedStringToWidth(text, width));
    }
}
