package top.cnpcplus.perf.client;

import top.cnpcplus.CnpcPlus;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RenderResourceCaches {
    private static final WeakInvalidationRegistry MODELS = new WeakInvalidationRegistry();
    private static final AtomicBoolean RESET = new AtomicBoolean();
    private static Object world;
    public static volatile boolean outlineCacheEnabled = true, blueprintCacheEnabled = true;
    public static volatile int outlineCapacity = 2048, blueprintMaxBlocks = 2048;
    public static volatile long blueprintBudgetNanos = 2_000_000;
    private RenderResourceCaches() {}
    public static void register(WeakInvalidationRegistry.Resettable model) { MODELS.register(model); }
    public static void requestReset() { RESET.set(true); }
    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            // Resource reload apply runs on the game executor; never touch GL on preparation workers.
            requestReset();
            flushReset();
        });
    }
    public static void tick(Object currentWorld) {
        if (world != currentWorld) { world = currentWorld; requestReset(); }
        flushReset();
    }
    public static void flushReset() {
        if (!RenderSystem.isOnRenderThreadOrInit()) return;
        if (!RESET.getAndSet(false)) return;
        Model2DOutlineCache.setCapacity(outlineCapacity);
        Model2DOutlineCache.invalidateAll();
        BlueprintPreviewCache.discard();
        try { MODELS.resetAll(); }
        catch (RuntimeException e) { LogManager.getLogger("CNPCOptimization").error("Reset render cache failed", e); }
    }
}
