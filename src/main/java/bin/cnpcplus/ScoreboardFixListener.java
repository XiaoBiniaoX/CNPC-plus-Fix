package bin.cnpcplus;

import net.minecraft.server.ServerScoreboard;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.List;

public class ScoreboardFixListener {
    private static final Logger LOGGER = CnpcPlus.LOGGER;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            ServerScoreboard scoreboard = event.getServer().getScoreboard();
            Field dirtyListenersField = findDirtyListenersField(scoreboard);
            if (dirtyListenersField == null) {
                LOGGER.warn("[CNPCPlus] Could not find dirtyListeners field in Scoreboard");
                return;
            }
            dirtyListenersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Runnable> dirtyListeners = (List<Runnable>) dirtyListenersField.get(scoreboard);
            if (dirtyListeners == null) {
                LOGGER.warn("[CNPCPlus] dirtyListeners list is null");
                return;
            }
            for (int i = 0; i < dirtyListeners.size(); i++) {
                Runnable original = dirtyListeners.get(i);
                dirtyListeners.set(i, () -> {
                    try {
                        original.run();
                    } catch (Exception e) {
                    }
                });
            }
            LOGGER.info("[CNPCPlus] Wrapped {} scoreboard dirty listeners with try-catch", dirtyListeners.size());
        } catch (Exception e) {
            LOGGER.error("[CNPCPlus] Failed to wrap scoreboard dirty listeners", e);
        }
    }

    private static Field findDirtyListenersField(ServerScoreboard scoreboard) {
        Class<?> clazz = scoreboard.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() == List.class && field.getName().equals("dirtyListeners")) {
                    return field;
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}