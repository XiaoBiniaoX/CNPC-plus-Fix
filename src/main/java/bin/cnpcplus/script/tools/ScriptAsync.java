package bin.cnpcplus.script.tools;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.Collectors;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Workers only receive immutable strings. All user callbacks are dispatched on the server thread. */
@Mod.EventBusSubscriber(modid = "cnpcplus")
public final class ScriptAsync {
    private static final Set<Task> ACTIVE = new LinkedHashSet<>();
    private static final AtomicInteger THREADS = new AtomicInteger();
    private static final LongAdder FINISHED = new LongAdder(), FAILED = new LongAdder(), NANOS = new LongAdder();
    private static ThreadPoolExecutor compute;
    private static ScheduledThreadPoolExecutor scheduler;
    public static final class Metrics {
        private final int pending, queuedCompute; private final long completed, failed, elapsedNanos;
        Metrics(int pending, int queued, long completed, long failed, long nanos) {
            this.pending = pending; queuedCompute = queued; this.completed = completed; this.failed = failed; elapsedNanos = nanos;
        }
        public int pending() { return pending; } public int queuedCompute() { return queuedCompute; }
        public long completed() { return completed; } public long failed() { return failed; } public long elapsedNanos() { return elapsedNanos; }
    }
    public Task submit(String operation, String input) { return supply(operation, input); }
    public Task supply(String operation, String input) {
        ScriptWorld.server();
        if (input == null || input.length() > 262144) return failed("INVALID_INPUT");
        Supplier<String> work;
        if ("SHA256".equals(operation)) work = () -> sha256(input);
        else if ("JSON_COMPACT".equals(operation)) work = () -> new JsonParser().parse(input).toString();
        else return failed("UNSUPPORTED_OPERATION");
        CompletableFuture<Object> result = new CompletableFuture<>(); Task task = track(result);
        if (task.isDone()) return task;
        if (compute == null) compute = new ThreadPoolExecutor(2, 2, 20, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(32), runnable -> thread(runnable, "compute"));
        try {
            Future<?> job = compute.submit(() -> {
                long start = System.nanoTime();
                try { if (!result.isDone()) result.complete(work.get()); }
                catch (RuntimeException error) { result.completeExceptionally(new IllegalArgumentException("OPERATION_FAILED")); }
                finally { NANOS.add(System.nanoTime() - start); }
            });
            result.whenComplete((value, error) -> { if (result.isCancelled()) job.cancel(false); });
        } catch (RejectedExecutionException rejected) { result.completeExceptionally(new IllegalStateException("TASK_REJECTED")); }
        return task;
    }
    public Task completed(String value) { return track(CompletableFuture.completedFuture(value)); }
    public Task failed(String error) { return track(failure(error)); }
    private static CompletableFuture<Object> failure(String error) {
        CompletableFuture<Object> result = new CompletableFuture<>(); result.completeExceptionally(new IllegalStateException(error)); return result;
    }
    public Task delay(int delayMillis) {
        CompletableFuture<Object> result = new CompletableFuture<>(); Task task = track(result);
        schedule(task, delayMillis, () -> result.complete(null)); return task;
    }
    public Task timeout(Task source, int timeoutMillis) {
        CompletableFuture<Object> result = new CompletableFuture<>(); Task task = track(result);
        schedule(task, timeoutMillis, () -> result.completeExceptionally(new IllegalStateException("TIMED_OUT")));
        source.future.whenComplete((value, error) -> transfer(result, value, error)); return task;
    }
    public Task all(Task[] tasks) {
        CompletableFuture<?>[] futures = Arrays.stream(tasks).map(task -> task.future).toArray(CompletableFuture<?>[]::new);
        return track(CompletableFuture.allOf(futures).thenApply(ignored -> Collections.unmodifiableList(
                Arrays.stream(futures).map(source -> source.getNow(null)).collect(Collectors.toList()))));
    }
    public Task any(Task[] tasks) {
        if (tasks.length == 0) return failed("EMPTY_GROUP");
        return track(CompletableFuture.anyOf(Arrays.stream(tasks).map(task -> task.future).toArray(CompletableFuture<?>[]::new)));
    }
    public Group createGroup() { ScriptWorld.server(); return new Group(); }
    public Metrics getMetrics() {
        ScriptWorld.server(); return new Metrics(ACTIVE.size(), compute == null ? 0 : compute.getQueue().size(), FINISHED.sum(), FAILED.sum(), NANOS.sum());
    }
    public static final class Group {
        private final Set<Task> tasks = new LinkedHashSet<>();
        public void add(Task task) { ScriptWorld.server(); tasks.removeIf(Task::isDone); if (!task.isDone()) tasks.add(task); }
        public void cancel() { ScriptWorld.server(); tasks.forEach(Task::cancel); tasks.clear(); }
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
            if (error != null) transfer(next, null, error); else next.complete(fn.apply(value));
        }); }
        public Task thenCompose(Function<Object, Task> fn) { return chain((next, value, error) -> {
            if (error != null) transfer(next, null, error); else fn.apply(value).future.whenComplete((output, failed) -> transfer(next, output, failed));
        }); }
        public Task thenAccept(Consumer<Object> fn) { return thenApply(value -> { fn.accept(value); return null; }); }
        public Task thenRun(Runnable fn) { return thenApply(value -> { fn.run(); return null; }); }
        public Task onSuccess(Consumer<Object> fn) { return thenAccept(fn); }
        public Task onFailure(Consumer<OperationResult.Diagnostic> fn) { return chain((next, value, error) -> {
            if (error != null) fn.accept(getError()); transfer(next, value, error);
        }); }
        public Task whenComplete(BiConsumer<Object, OperationResult.Diagnostic> fn) { return chain((next, value, error) -> {
            fn.accept(value, error == null ? null : getError()); transfer(next, value, error);
        }); }
        public Task recover(Function<OperationResult.Diagnostic, Object> fn) { return chain((next, value, error) -> next.complete(error == null ? value : fn.apply(getError()))); }
        private Task chain(Continuation continuation) {
            ScriptWorld.server(); CompletableFuture<Object> result = new CompletableFuture<>(); Task next = track(result);
            if (next.isDone()) return next;
            BooleanSupplier source = owner, subscriber = next.owner;
            next.owner = () -> source.getAsBoolean() && subscriber.getAsBoolean();
            callbacks.add(() -> {
                if (result.isDone()) return;
                if (isCancelled() || !next.owner.getAsBoolean()) { result.cancel(false); return; }
                future.handle((value, error) -> {
                    try { ScriptScope.callback(next.owner, () -> continuation.run(result, value, error)); }
                    catch (RuntimeException callbackError) { result.completeExceptionally(new IllegalStateException("CALLBACK_FAILED")); }
                    return null;
                });
            });
            ACTIVE.add(this); return next;
        }
    }
    @FunctionalInterface private interface Continuation { void run(CompletableFuture<Object> target, Object value, Throwable error); }
    private static Task track(CompletableFuture<Object> future) {
        ScriptWorld.server();
        if (ACTIVE.size() >= 256) return new Task(failure("TASK_REJECTED"), ScriptScope.current());
        Task task = new Task(future, ScriptScope.current()); ACTIVE.add(task); return task;
    }
    private static void schedule(Task task, int millis, Runnable run) {
        if (task.isDone()) return;
        if (millis < 0) { task.future.completeExceptionally(new IllegalArgumentException("INVALID_DURATION")); return; }
        if (scheduler == null) {
            scheduler = new ScheduledThreadPoolExecutor(1, runnable -> thread(runnable, "timer")); scheduler.setRemoveOnCancelPolicy(true);
        }
        ScheduledFuture<?> timer = scheduler.schedule(run, millis, TimeUnit.MILLISECONDS);
        task.future.whenComplete((value, error) -> timer.cancel(false));
    }
    private static String reason(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? reason(error.getCause()) : error.getMessage();
    }
    private static void transfer(CompletableFuture<Object> next, Object value, Throwable error) {
        if (error == null) next.complete(value); else next.completeExceptionally(error);
    }
    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) hex.append(Character.forDigit((value & 255) >>> 4, 16)).append(Character.forDigit(value & 15, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
    private static Thread thread(Runnable runnable, String kind) {
        Thread thread = new Thread(runnable, "CNPCplus-async-" + kind + "-" + THREADS.incrementAndGet()); thread.setDaemon(true); return thread;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        for (Task task : new ArrayList<>(ACTIVE)) {
            if (!task.owner.getAsBoolean()) { task.future.cancel(false); task.callbacks.clear(); }
            if (!task.isDone()) continue;
            ACTIVE.remove(task);
            if (!task.counted) { task.counted = true; FINISHED.increment(); if (task.getState().equals("FAILED")) FAILED.increment(); }
            List<Runnable> ready = new ArrayList<>(task.callbacks); task.callbacks.clear(); ready.forEach(Runnable::run);
        }
    }
    public static void stop() {
        for (Task task : ACTIVE) { task.future.cancel(false); task.callbacks.clear(); task.owner = () -> false; }
        ACTIVE.clear();
        if (compute != null) { compute.shutdownNow(); compute = null; }
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        FINISHED.reset(); FAILED.reset(); NANOS.reset();
    }
}
