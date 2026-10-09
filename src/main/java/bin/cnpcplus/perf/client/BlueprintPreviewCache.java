package bin.cnpcplus.perf.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import noppes.npcs.blocks.tiles.TileBuilder;
import noppes.npcs.schematics.SchematicWrapper;
import org.apache.logging.log4j.LogManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 渲染线程分步构建预览。时间预算只让出计算，不丢弃待处理方块。
 * 保留此前双端协议的 100000 上限；不更改实际建造、网络内容或选区。
 * 同一批 BufferBuilder 跨帧保留，避免因时间预算过短产生大量微小 VBO。
 *
 * <p>1.21.1 移植要点：BufferBuilder 改为 {@code new BufferBuilder(ByteBufferBuilder, Mode, Format)}
 * 一步构造（构造即处于 building 态），收尾用 {@code build()}（返回 {@code MeshData}，空网格为 null，
 * 未构建态抛 IllegalStateException —— 见 {@link #finishQuietly}）；{@code MeshData} 用 {@code close()}
 * 释放（1.20.1 是 {@code RenderedBuffer.release()}）；{@code VertexBuffer.upload(MeshData)}
 * 无论成功还是抛异常都会 close MeshData；{@code AABB} 无 (Vec3i,Vec3i) 构造器，改用 6-double 构造器；
 * {@code ModelData} 来自 {@code net.neoforged.neoforge.client.model.data.ModelData}。
 */
public final class BlueprintPreviewCache {
    public static final int RENDER_LIMIT = 100_000;
    private static final int BATCH_BLOCKS = 2048;
    private static final int BUILDER_CAPACITY = 64 * 1024;
    private record Batch(RenderType type, VertexBuffer vbo) {}
    private static final List<Batch> BATCHES = new ArrayList<>();
    private static final Map<RenderType, BufferBuilder> PENDING = new IdentityHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong();
    private static Object world, schema, wrapper;
    private static String shape;
    private static long revision, lastDrawNanos;
    private static int nextIndex, pendingBlocks;
    private static boolean complete;

    private BlueprintPreviewCache() {}
    public static void changed() { REVISION.incrementAndGet(); }

    public static void render(PoseStack pose, TileBuilder tile, SchematicWrapper schem) {
        RenderResourceCaches.flushReset();
        String want = schem.schema.getName() + "|" + schem.size + "|" + schem.schema.getWidth()
                + "|" + schem.schema.getHeight() + "|" + schem.schema.getLength()
                + "|" + tile.rotation + "|" + tile.getBlockPos().asLong();
        long currentRevision = REVISION.get();
        if (world != Minecraft.getInstance().level || schema != schem.schema || wrapper != schem
                || !want.equals(shape) || revision != currentRevision) {
            discard();
            world = Minecraft.getInstance().level;
            schema = schem.schema; wrapper = schem; shape = want; revision = currentRevision;
        }
        lastDrawNanos = System.nanoTime();
        if (!complete) bakeSome(tile, schem);
        pose.pushPose();
        try {
            pose.translate(1.0f, tile.yOffest, 1.0f);
            drawBatches(pose);
            drawSelectionBox(pose, tile, schem);
        } finally { pose.popPose(); }
    }

    private static void bakeSome(TileBuilder tile, SchematicWrapper schem) {
        int limit = Math.min(schem.size, RENDER_LIMIT);
        if (schem.schema.getWidth() <= 0 || schem.schema.getLength() <= 0) {
            complete = true;
            return;
        }
        FrameWorkBudget budget = new FrameWorkBudget(RenderResourceCaches.blueprintBudgetNanos,
                RenderResourceCaches.blueprintMaxBlocks, System::nanoTime);
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        PoseStack local = new PoseStack();
        while (nextIndex < limit && budget.mayContinue()) {
            BlockState state;
            try { state = schem.schema.getBlockState(nextIndex); }
            catch (IndexOutOfBoundsException e) {
                // 旧服务端可能只下发了 25000 方块；保留它实际提供的数据，不越界。
                complete = true;
                break;
            }
            int i = nextIndex++;
            pendingBlocks++;
            budget.completedBlock();
            if (state != null && state.getRenderShape() == RenderShape.MODEL) {
                appendBlock(dispatcher, local, tile, schem, state, i);
            }
            if (pendingBlocks >= BATCH_BLOCKS) uploadPending();
        }
        if (nextIndex >= limit) complete = true;
        if (complete) uploadPending();
    }

    private static void appendBlock(BlockRenderDispatcher dispatcher, PoseStack local, TileBuilder tile,
                                    SchematicWrapper schem, BlockState state, int i) {
        int width = schem.schema.getWidth(), length = schem.schema.getLength();
        int x = i % width, z = i / width % length, y = i / width / length;
        BlockPos rotated = schem.rotatePos(x, y, z, tile.rotation);
        BlockState finalState = schem.rotationState(state, tile.rotation);
        RenderType type = ItemBlockRenderTypes.getRenderType(finalState, false);
        // 1.21.1：BufferBuilder 一步构造（ByteBufferBuilder + Mode + Format），构造即 building 态。
        BufferBuilder builder = PENDING.computeIfAbsent(type, key ->
                new BufferBuilder(new ByteBufferBuilder(BUILDER_CAPACITY), key.mode(), key.format()));
        local.pushPose();
        try {
            local.translate(rotated.getX(), rotated.getY(), rotated.getZ());
            // 1.21.1 NeoForge 重载：renderModel(..., ModelData, RenderType)。
            dispatcher.getModelRenderer().renderModel(local.last(), builder, finalState,
                    dispatcher.getBlockModel(finalState), 1, 1, 1, 0xF000F0,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        } finally { local.popPose(); }
    }

    /**
     * 1.21.1 的收尾：{@code build()} 在网格非空时返回 {@code MeshData}、空网格返回 null、
     * builder 已不在 building 态时抛 IllegalStateException。本方法把后两种都折叠成 null，
     * 等价于 1.20.1 的 {@code endOrDiscardIfEmpty()}。
     */
    private static MeshData finishQuietly(BufferBuilder builder) {
        try {
            return builder.build();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private static void uploadPending() {
        try {
            for (var entry : PENDING.entrySet()) {
                MeshData mesh = finishQuietly(entry.getValue());
                if (mesh == null) continue;
                VertexBuffer vbo = null;
                boolean uploaded = false, handedToUpload = false;
                try {
                    vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    vbo.bind();
                    if (vbo.isInvalid()) throw new IllegalStateException("Invalid preview buffer");
                    handedToUpload = true;
                    // MC 1.21.1 upload 在成功和抛异常时都 close MeshData（等价于 1.20.1 的 release）。
                    vbo.upload(mesh);
                    BATCHES.add(new Batch(entry.getKey(), vbo));
                    uploaded = true;
                } finally {
                    if (!handedToUpload) mesh.close();
                    if (!uploaded && vbo != null) vbo.close();
                }
            }
        } finally {
            for (BufferBuilder builder : PENDING.values()) {
                MeshData remaining = finishQuietly(builder);
                if (remaining != null) remaining.close();
            }
            PENDING.clear(); pendingBlocks = 0;
            VertexBuffer.unbind();
        }
    }

    private static void drawBatches(PoseStack pose) {
        try {
            for (Batch batch : BATCHES) {
                batch.type.setupRenderState();
                try {
                    ShaderInstance shader = RenderSystem.getShader();
                    if (shader != null) {
                        batch.vbo.bind();
                        batch.vbo.drawWithShader(pose.last().pose(), RenderSystem.getProjectionMatrix(), shader);
                    }
                } finally { batch.type.clearRenderState(); }
            }
        } finally { VertexBuffer.unbind(); }
    }

    private static void drawSelectionBox(PoseStack pose, TileBuilder tile, SchematicWrapper schem) {
        BlockPos size = tile.rotation % 2 == 0
                ? new BlockPos(schem.schema.getWidth(), schem.schema.getHeight(), schem.schema.getLength())
                : new BlockPos(schem.schema.getLength(), schem.schema.getHeight(), schem.schema.getWidth());
        pose.pushPose();
        try {
            pose.translate(0.001f, 0.001f, 0.001f);
            // 1.21.1：AABB 无 (Vec3i, Vec3i) 构造器，改用 6-double 构造器（起点为 BlockPos.ZERO = 0,0,0）。
            LevelRenderer.renderLineBox(pose, Minecraft.getInstance().renderBuffers().bufferSource()
                    .getBuffer(RenderType.lines()),
                    new AABB(0.0, 0.0, 0.0, size.getX(), size.getY(), size.getZ()),
                    1, 0, 0, 1);
        } finally { pose.popPose(); }
    }

    public static void discard() {
        for (Batch batch : BATCHES) {
            try { batch.vbo.close(); }
            catch (RuntimeException e) { LogManager.getLogger("CNPCOptimization").warn("Close preview buffer failed", e); }
        }
        BATCHES.clear();
        for (BufferBuilder builder : PENDING.values()) {
            MeshData rendered = finishQuietly(builder);
            if (rendered != null) rendered.close();
        }
        PENDING.clear(); pendingBlocks = 0;
        world = schema = wrapper = null; shape = null; nextIndex = 0; complete = false;
    }
    public static boolean hasCache() { return shape != null; }
    public static long sinceLastDraw() { return System.nanoTime() - lastDrawNanos; }
}
