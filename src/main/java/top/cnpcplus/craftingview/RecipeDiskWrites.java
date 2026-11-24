package top.cnpcplus.craftingview;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = "cnpcplus")
public final class RecipeDiskWrites {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "cnpcplus-recipe-disk"); thread.setDaemon(true); return thread;
    });
    private RecipeDiskWrites() {}
    public static CompletableFuture<Void> save(Path target, CompoundTag root, boolean keepBackup) {
        CompoundTag snapshot = root.copy();
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
                try (var output = Files.newOutputStream(temporary)) { NbtIo.writeCompressed(snapshot, output); }
                if (keepBackup && Files.isRegularFile(target))
                    Files.copy(target, target.resolveSibling(target.getFileName() + "_old"), StandardCopyOption.REPLACE_EXISTING);
                try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException e) { throw new CompletionException(e); }
        }, WRITER).whenComplete((done, error) -> {
            if (error != null) LoggerFactory.getLogger("cnpcplus").error("Cannot save recipes to {}", target, error);
        });
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { awaitPending(); }
    public static void awaitPending() {
        try { WRITER.submit(() -> {}).get(10, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (ExecutionException | TimeoutException e) { LoggerFactory.getLogger("cnpcplus").error("Waiting for recipe saves failed", e); }
    }
}
