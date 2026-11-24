package top.cnpcplus.script.tools;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Only detached data operations enter worker threads. Script callbacks always run on the server thread. */
@EventBusSubscriber(modid = "cnpcplus")
public final class ScriptAsync {
    private static final Set<Task> ACTIVE = new LinkedHashSet<>();
    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final LongAdder FINISHED = new LongAdder(), FAILED = new LongAdder(), NANOS = new LongAdder();
    private static ThreadPoolExecutor compute;
    private static ScheduledThreadPoolExecutor scheduler;

    public record Metrics(int pending, int queuedCompute, long completed, long failed, long elapsedNanos) {}

    public Task submit(String operation, String input) { return supply(operation, input); }
    public Task supply(String operation, String input) {
        ScriptWorld.server();
        if (input == null || input.length() > 262_144) return failed("INVALID_INPUT");
        Supplier<String> work = switch (operation) {
            case "SHA256" -> () -> sha256(input);
            case "JSON_COMPACT" -> () -> JsonParser.parseString(input).toString();
            default -> null;
        };
        if (work == null) return failed("UNSUPPORTED_OPERATION");
        var result = new CompletableFuture<Object>();
        Task task = track(result);
        if (task.isDone()) return task;
        if (compute == null) compute = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32), runnable -> thread(runnable, "compute"));
        try {
            var job = compute.submit(() -> {
                long start = System.nanoTime();
                try {
                    if (!result.isDone()) result.complete(work.get());
                } catch (RuntimeException error) {
                    result.completeExceptionally(new IllegalArgumentException("OPERATION_FAILED"));
                } finally {
                    NANOS.add(System.nanoTime() - start);
                }
            });
            result.whenComplete((value, error) -> { if (result.isCancelled()) job.cancel(false); });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(new IllegalStateException("TASK_REJECTED"));
        }
        return task;
    }

    public Task completed(String value) { return track(CompletableFuture.completedFuture(value)); }
    public Task failed(String error) { return track(CompletableFuture.failedFuture(new IllegalStateException(error))); }

    public Task delay(int delayMillis) {
        var result = new CompletableFuture<Object>();
        Task task = track(result);
        schedule(task, delayMillis, () -> result.complete(null));
        return task;
    }

    public Task timeout(Task source, int timeoutMillis) {
        var result = new CompletableFuture<Object>();
        Task task = track(result);
        schedule(task, timeoutMillis, () -> result.completeExceptionally(new IllegalStateException("TIMED_OUT")));
        source.future.whenComplete((value, failure) -> transfer(result, value, failure));
        return task;
    }

    public Task all(Task[] tasks) {
        var sources = futures(tasks);
        return track(CompletableFuture.allOf(sources).thenApply(ignored ->
                java.util.Arrays.stream(sources).map(source -> source.getNow(null)).toList()));
    }
    public Task any(Task[] tasks) {
        if (tasks.length == 0) return failed("EMPTY_GROUP");
        return track(CompletableFuture.anyOf(futures(tasks)));
    }
    public Group createGroup() { ScriptWorld.server(); return new Group(); }
    public Metrics getMetrics() {
        ScriptWorld.server();
        return new Metrics(ACTIVE.size(), compute == null ? 0 : compute.getQueue().size(), FINISHED.sum(), FAILED.sum(), NANOS.sum());
    }

    public static final class Group {
        private final Set<Task> tasks = new LinkedHashSet<>();
        public void add(Task task) {
            ScriptWorld.server();
            tasks.removeIf(Task::isDone);
            if (!task.isDone()) tasks.add(task);
        }
        public void cancel() {
            ScriptWorld.server();
            tasks.forEach(Task::cancel);
            tasks.clear();
        }
    }

    public static final class Task {
        private final CompletableFuture<Object> future;
        private BooleanSupplier owner;
        private final List<Runnable> callbacks = new ArrayList<>();
        private boolean counted;

        private Task(CompletableFuture<Object> future, BooleanSupplier owner) { this.future = future; this.owner = owner; }
        public boolean cancel() { ScriptWorld.server(); return future.cancel(false); }
        public boolean isCancelled() { return future.isCancelled(); }
        public boolean isDone() { return future.isDone(); }
        public String getState() { return isCancelled() ? "CANCELLED" : !isDone() ? "PENDING" : future.isCompletedExceptionally() ? "FAILED" : "COMPLETED"; }
        public OperationResult.Diagnostic getError() {
            if (!future.isCompletedExceptionally() || isCancelled()) return null;
            return future.handle((value, error) -> OperationResult.failure("ASYNC", reason(error)).getDiagnostic()).getNow(null);
        }

        public Task thenApply(Function<Object, Object> fn) { return chain((next, value, error) -> {
            if (error != null) transfer(next, null, error);
            else next.complete(fn.apply(value));
        }); }
        public Task thenCompose(Function<Object, Task> fn) { return chain((next, value, error) -> {
            if (error != null) transfer(next, null, error);
            else fn.apply(value).future.whenComplete((output, failed) -> transfer(next, output, failed));
        }); }
        public Task thenAccept(Consumer<Object> fn) { return thenApply(value -> { fn.accept(value); return null; }); }
        public Task thenRun(Runnable fn) { return thenApply(value -> { fn.run(); return null; }); }
        public Task onSuccess(Consumer<Object> fn) { return thenAccept(fn); }
        public Task onFailure(Consumer<OperationResult.Diagnostic> fn) { return chain((next, value, error) -> {
            if (error != null) fn.accept(getError());
            transfer(next, value, error);
        }); }
        public Task whenComplete(BiConsumer<Object, OperationResult.Diagnostic> fn) { return chain((next, value, error) -> {
            fn.accept(value, error == null ? null : getError());
            transfer(next, value, error);
        }); }
        public Task recover(Function<OperationResult.Diagnostic, Object> fn) { return chain((next, value, error) -> {
            next.complete(error == null ? value : fn.apply(getError()));
        }); }

        private Task chain(Continuation continuation) {
            ScriptWorld.server();
            var result = new CompletableFuture<Object>();
            Task next = track(result);
            if (next.isDone()) return next;
            callbacks.add(() -> {
                if (result.isDone()) return;
                if (isCancelled() || !next.owner.getAsBoolean()) { result.cancel(false); return; }
                future.handle((value, error) -> {
                    try {
                        ScriptScope.callback(next.owner, () -> continuation.run(result, value, error));
                    } catch (RuntimeException callbackError) {
                        result.completeExceptionally(new IllegalStateException("CALLBACK_FAILED"));
                    }
                    return null;
                });
            });
            ACTIVE.add(this);
            return next;
        }
    }

    @FunctionalInterface private interface Continuation {
        void run(CompletableFuture<Object> target, Object value, Throwable error);
    }

    private static Task track(CompletableFuture<Object> future) {
        ScriptWorld.server();
        if (ACTIVE.size() >= 256) return new Task(CompletableFuture.failedFuture(new IllegalStateException("TASK_REJECTED")), ScriptScope.current());
        Task task = new Task(future, ScriptScope.current());
        ACTIVE.add(task);
        return task;
    }

    private static void schedule(Task task, int millis, Runnable run) {
        if (task.isDone()) return;
        if (millis < 0) { task.future.completeExceptionally(new IllegalArgumentException("INVALID_DURATION")); return; }
        if (scheduler == null) {
            scheduler = new ScheduledThreadPoolExecutor(1, runnable -> thread(runnable, "timer"));
            scheduler.setRemoveOnCancelPolicy(true);
        }
        var timer = scheduler.schedule(run, millis, TimeUnit.MILLISECONDS);
        task.future.whenComplete((value, error) -> timer.cancel(false));
    }

    private static CompletableFuture<?>[] futures(Task[] tasks) {
        return java.util.Arrays.stream(tasks).map(task -> task.future).toArray(CompletableFuture<?>[]::new);
    }

    private static String reason(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? reason(error.getCause()) : error.getMessage();
    }

    private static void transfer(CompletableFuture<Object> next, Object value, Throwable error) {
        if (error == null) next.complete(value);
        else next.completeExceptionally(error);
    }

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }

    private static Thread thread(Runnable runnable, String kind) {
        Thread thread = new Thread(runnable, "CNPCplus-async-" + kind + "-" + THREADS.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Pre event) {
        for (Task task : List.copyOf(ACTIVE)) {
            if (task.owner != null && !task.owner.getAsBoolean()) {
                task.future.cancel(false);
                task.callbacks.clear();
            }
            if (!task.isDone()) continue;
            ACTIVE.remove(task);
            if (!task.counted) {
                task.counted = true;
                FINISHED.increment();
                if (task.getState().equals("FAILED")) FAILED.increment();
            }
            var ready = List.copyOf(task.callbacks);
            task.callbacks.clear();
            ready.forEach(Runnable::run);
        }
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        for (Task task : ACTIVE) {
            task.future.cancel(false);
            task.callbacks.clear();
            task.owner = null;
        }
        ACTIVE.clear();
        if (compute != null) { compute.shutdownNow(); compute = null; }
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        FINISHED.reset(); FAILED.reset(); NANOS.reset();
    }
}
