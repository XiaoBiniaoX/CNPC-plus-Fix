package top.cnpcplus.craftingview;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.LoggerFactory;
import top.cnpcplus.persist.FileReplacement;

public final class RecipeDiskWrites {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cnpcplus-recipe-disk");
        thread.setDaemon(true);
        return thread;
    });

    private RecipeDiskWrites() {}

    /** Capture the NBT on the caller's thread. Only disk I/O touches the worker. */
    public static CompletableFuture<Void> save(Path target, CompoundTag root, boolean keepBackup) {
        CompoundTag snapshot = root.copy();
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(target.getParent());
                Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
                try (var output = Files.newOutputStream(temporary)) {
                    NbtIo.writeCompressed(snapshot, output);
                }
                if (keepBackup && Files.isRegularFile(target)) {
                    Files.copy(target, target.resolveSibling(target.getFileName() + "_old"), StandardCopyOption.REPLACE_EXISTING);
                }
                FileReplacement.replace(temporary, target);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, WRITER).whenComplete((done, error) -> {
            if (error != null) LoggerFactory.getLogger("cnpcplus").error("Cannot save recipes to {}", target, error);
        });
    }

    public static void awaitPending() {
        try {
            WRITER.submit(() -> {}).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            LoggerFactory.getLogger("cnpcplus").error("Waiting for recipe saves failed", e);
        }
    }
}
