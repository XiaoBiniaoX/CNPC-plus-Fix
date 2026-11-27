package top.cnpcplus.quest;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import org.slf4j.Logger;

/** Server-thread world sampling plus bounded, pure-Java visibility aggregation. */
public final class QuestRegionRecorder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "cnpcplus-quest-explore");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentLinkedQueue<Completion> COMPLETED = new ConcurrentLinkedQueue<>();
    private static long nextScan;
    private static long nextToken;
    private static Ticket inFlight;

    private record Ticket(long token, ServerPlayer player, PlayerData playerData, QuestData data,
                          QuestExplore quest, String dimension) {}
    private record Completion(long token, BitSet cells, Throwable failure) {}

    private QuestRegionRecorder() {}

    public static void tick(MinecraftServer server) {
        finishScan(server);
        if (inFlight != null || server.getTickCount() % 2 != 0) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) { nextScan = 0; return; }
        long scan = nextScan++;
        ServerPlayer player = players.get((int)Math.floorMod(scan, players.size()));
        PlayerData pdata = PlayerData.get(player);
        List<QuestData> quests = pdata.questData.activeQuests.values().stream()
                .filter(data -> data.quest.type == 7 && data.quest.questInterface instanceof QuestExplore)
                .toList();
        if (quests.isEmpty()) return;
        QuestData data = quests.get(questIndex(scan, players.size(), quests.size()));
        submit(player, pdata, data, (QuestExplore)data.quest.questInterface);
    }

    private static void submit(ServerPlayer player, PlayerData pdata, QuestData data, QuestExplore quest) {
        long total = quest.cellCount(player.level().getMinBuildHeight(), player.level().getMaxBuildHeight());
        if (total <= 0 || total > QuestExplore.MAX_CELLS) return;
        String dimension = player.level().dimension().location().toString();
        if (!QuestExplore.dimensionMatches(data, dimension)) return;

        int minX = Math.min(quest.x1, quest.x2), maxX = Math.max(quest.x1, quest.x2);
        int minY = Math.max(Math.min(quest.y1, quest.y2), player.level().getMinBuildHeight());
        int maxY = Math.min(Math.max(quest.y1, quest.y2), player.level().getMaxBuildHeight() - 1);
        int minZ = Math.min(quest.z1, quest.z2), maxZ = Math.max(quest.z1, quest.z2);
        if (maxY < minY) return;

        int baseCellX = Math.floorDiv(minX, 4), baseCellY = Math.floorDiv(minY, 4), baseCellZ = Math.floorDiv(minZ, 4);
        int sizeY = Math.floorDiv(maxY, 4) - baseCellY + 1;
        int sizeZ = Math.floorDiv(maxZ, 4) - baseCellZ + 1;
        List<int[]> rays = new ArrayList<>(15);
        Vec3 look = player.getViewVector(1.0F);
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 up = right.cross(look).normalize();
        Vec3 start = player.getEyePosition();
        for (int yi = -1; yi <= 1; yi++) {
            for (int xi = -2; xi <= 2; xi++) {
                Vec3 direction = look.add(right.scale(xi * 0.28D)).add(up.scale(yi * 0.32D)).normalize();
                Vec3 end = start.add(direction.scale(quest.viewDistance));
                BlockHitResult hit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                double stop = hit.getType() == BlockHitResult.Type.MISS ? quest.viewDistance : start.distanceTo(hit.getLocation());
                BitSet cells = new BitSet();
                for (double d = 1.0D; d < stop; d += 2.0D) {
                    BlockPos pos = BlockPos.containing(start.x + direction.x * d, start.y + direction.y * d, start.z + direction.z * d);
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    cells.set(exploreCellIndex(quest, pos.getX(), pos.getY(), pos.getZ(),
                            baseCellX, baseCellY, baseCellZ, sizeY, sizeZ));
                }
                // A visible solid surface belongs to the region even when no air sample enters it.
                if (hit.getType() != BlockHitResult.Type.MISS) {
                    BlockPos pos = hit.getBlockPos();
                    if (pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                            && pos.getZ() >= minZ && pos.getZ() <= maxZ)
                        cells.set(exploreCellIndex(quest, pos.getX(), pos.getY(), pos.getZ(),
                                baseCellX, baseCellY, baseCellZ, sizeY, sizeZ));
                }
                rays.add(cells.stream().toArray());
            }
        }

        long token = ++nextToken;
        inFlight = new Ticket(token, player, pdata, data, quest, dimension);
        int[][] snapshot = rays.toArray(int[][]::new);
        WORKER.execute(() -> {
            try {
                COMPLETED.add(new Completion(token, visibleCells(snapshot), null));
            } catch (Throwable failure) {
                COMPLETED.add(new Completion(token, null, failure));
            }
        });
    }

    /** Pure-Java aggregation stage: any sampled visible ray can contribute a cell. */
    public static BitSet visibleCells(int[][] rays) {
        BitSet visible = new BitSet();
        for (int[] ray : rays)
            for (int cell : ray) if (cell >= 0 && cell < QuestExplore.MAX_CELLS) visible.set(cell);
        return visible;
    }

    static int cellIndex(int worldX, int worldY, int worldZ, int baseCellX, int baseCellY, int baseCellZ,
                         int sizeY, int sizeZ) {
        int x = Math.floorDiv(worldX, 4) - baseCellX;
        int y = Math.floorDiv(worldY, 4) - baseCellY;
        int z = Math.floorDiv(worldZ, 4) - baseCellZ;
        return (x * sizeY + y) * sizeZ + z;
    }

    static int horizontalCellIndex(int worldX, int worldZ, int baseCellX, int baseCellZ, int sizeZ) {
        return (Math.floorDiv(worldX, 4) - baseCellX) * sizeZ + Math.floorDiv(worldZ, 4) - baseCellZ;
    }

    private static int exploreCellIndex(QuestExplore quest, int worldX, int worldY, int worldZ,
                                        int baseCellX, int baseCellY, int baseCellZ, int sizeY, int sizeZ) {
        return quest.horizontalCoverage
                ? horizontalCellIndex(worldX, worldZ, baseCellX, baseCellZ, sizeZ)
                : cellIndex(worldX, worldY, worldZ, baseCellX, baseCellY, baseCellZ, sizeY, sizeZ);
    }

    static int questIndex(long scan, int players, int quests) {
        return (int)Math.floorMod(Math.floorDiv(scan, players), quests);
    }

    private static void finishScan(MinecraftServer server) {
        Completion completion;
        while ((completion = COMPLETED.poll()) != null) {
            Ticket ticket = inFlight;
            if (ticket == null || completion.token() != ticket.token()) continue;
            inFlight = null;
            if (completion.failure() != null) {
                LOGGER.error("Quest exploration visibility aggregation failed", completion.failure());
                continue;
            }
            ServerPlayer player = ticket.player();
            if (player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player
                    || !player.level().dimension().location().toString().equals(ticket.dimension())
                    || ticket.playerData().questData.activeQuests.get(ticket.data().quest.id) != ticket.data()
                    || ticket.data().quest.questInterface != ticket.quest()) continue;
            String savedDimension = ticket.data().extraData.getString("CNPCPlusExploreDimension");
            if (!savedDimension.isEmpty() && !savedDimension.equals(ticket.dimension())) continue;
            BitSet explored = BitSet.valueOf(ticket.data().extraData.getLongArray("CNPCPlusExploreCells"));
            boolean modeChanged = !ticket.quest().progressModeMatches(ticket.data().extraData);
            if (modeChanged) explored.clear();
            BitSet fresh = completion.cells();
            boolean discovered = recordNewCells(explored, fresh);
            if (!modeChanged && !discovered) continue;
            if (savedDimension.isEmpty()) ticket.data().extraData.putString("CNPCPlusExploreDimension", ticket.dimension());
            ticket.data().extraData.putBoolean(QuestExplore.COVERAGE_MODE_KEY, ticket.quest().horizontalCoverage);
            ticket.data().extraData.putLongArray("CNPCPlusExploreCells", explored.toLongArray());
            ticket.playerData().updateClient = true;
            if (discovered) ticket.playerData().questData.checkQuestCompletion(player, 7);
        }
    }

    public static boolean recordNewCells(BitSet explored, BitSet visible) {
        visible.andNot(explored);
        if (visible.isEmpty()) return false;
        explored.or(visible);
        return true;
    }
}
