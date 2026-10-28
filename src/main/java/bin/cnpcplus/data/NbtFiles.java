package bin.cnpcplus.data;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 配方/外置 NPC 数据共用：有序快照写盘、原子替换，读取错误交给调用方处理。 */
public final class NbtFiles {
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cnpcplus-data-writer");
        thread.setDaemon(true);
        return thread;
    });

    private NbtFiles() { }

    public static NBTTagCompound read(Path file) throws IOException {
        if (!Files.exists(file)) return new NBTTagCompound();
        try (InputStream in = Files.newInputStream(file)) { return CompressedStreamTools.readCompressed(in); }
    }

    public static CompletableFuture<Void> write(Path file, NBTTagCompound snapshot) {
        final NBTTagCompound copy = snapshot.copy();
        return CompletableFuture.runAsync(() -> {
            try { writeNow(file, copy); }
            catch (IOException error) {
                CnpcPlus.LOGGER.error("Cannot save CNPCPlus data: {}", file, error);
                throw new java.util.concurrent.CompletionException(error);
            }
        }, WRITER);
    }

    public static CompletableFuture<Void> writeBytes(Path file, byte[] data) {
        final byte[] copy = data.clone();
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(temporary, copy);
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            } catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
        }, WRITER);
    }

    public static void writeNow(Path file, NBTTagCompound snapshot) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(temporary)) { CompressedStreamTools.writeCompressed(snapshot, out); }
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
    }

    public static void flush() {
        try { WRITER.submit(() -> { }).get(30, TimeUnit.SECONDS); }
        catch (Exception error) { CnpcPlus.LOGGER.error("CNPCPlus pending data writes did not finish", error); }
    }
}
