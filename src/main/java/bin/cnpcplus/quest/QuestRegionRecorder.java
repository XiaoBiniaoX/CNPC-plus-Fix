package bin.cnpcplus.quest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;

/** Shared region sampler. World access and progress writes stay on the server thread. */
public final class QuestRegionRecorder {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cnpcplus-quest-explore");
        thread.setDaemon(true);
        return thread;
    });
    private static final ConcurrentLinkedQueue<Completion> COMPLETED = new ConcurrentLinkedQueue<>();
    private static long scanCursor, tokenCounter;
    private static Ticket inFlight;

    private static final class Ticket {
        final long token;
        final EntityPlayer player;
        final PlayerData playerData;
        final QuestData data;
        final QuestExplore quest;
        final int dimension;
        final boolean horizontalCoverage;
        Ticket(long token, EntityPlayer player, PlayerData playerData, QuestData data, QuestExplore quest, int dimension) {
            this.token = token; this.player = player; this.playerData = playerData;
            this.data = data; this.quest = quest; this.dimension = dimension;
            this.horizontalCoverage = quest.horizontalCoverage;
        }
    }
    private static final class Completion {
        final long token;
        final int[] cells;
        Completion(long token, int[] cells) { this.token = token; this.cells = cells; }
    }

    private QuestRegionRecorder() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side == Side.SERVER && event.phase == TickEvent.Phase.START)
            tick(FMLCommonHandler.instance().getMinecraftServerInstance());
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        finish(server);
        if (inFlight != null) return;
        List<EntityPlayer> players = new ArrayList<EntityPlayer>(server.getPlayerList().getPlayers());
        if (players.isEmpty()) { scanCursor = 0; return; }
        long cursor = scanCursor++;
        EntityPlayer player = players.get((int)(cursor % players.size()));
        if (!player.isEntityAlive() || player.isSpectator()) return;
        PlayerData pdata = PlayerData.get(player);
        List<QuestData> quests = new ArrayList<>();
        for (QuestData data : pdata.questData.activeQuests.values())
            if (data.quest.type == 7 && data.quest.questInterface instanceof QuestExplore) quests.add(data);
        if (quests.isEmpty()) return;
        QuestData data = quests.get((int)((cursor / players.size()) % quests.size()));
        submit(player, pdata, data, (QuestExplore)data.quest.questInterface);
    }

    private static void submit(EntityPlayer player, PlayerData pdata, QuestData data, QuestExplore quest) {
        long total = quest.cellCount();
        if (total <= 0) return;
        int dimension = player.world.provider.getDimension();
        if (!QuestExplore.dimensionMatches(data, dimension)) return;
        int minX = quest.x1, maxX = quest.x2;
        int minY = Math.max(0, quest.y1), maxY = Math.min(255, quest.y2);
        int minZ = quest.z1, maxZ = quest.z2;
        int baseX = Math.floorDiv(minX, 4), baseY = Math.floorDiv(minY, 4), baseZ = Math.floorDiv(minZ, 4);
        int sizeY = Math.floorDiv(maxY, 4) - baseY + 1, sizeZ = Math.floorDiv(maxZ, 4) - baseZ + 1;
        Vec3d look = player.getLookVec();
        double yaw = Math.toRadians(player.rotationYaw);
        Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d up = look.crossProduct(right).normalize();
        Vec3d start = player.getPositionEyes(1);
        List<int[]> rays = new ArrayList<>(15);
        // One player/quest per tick, 15 rays, <=128 blocks; never loads an unseen chunk.
        for (int yi = -1; yi <= 1; yi++) for (int xi = -2; xi <= 2; xi++) {
            Vec3d direction = look.add(right.scale(xi * .28)).add(up.scale(yi * .32)).normalize();
            double range = quest.viewDistance;
            for (double distance = 0; distance <= range; distance += 1) {
                BlockPos pos = new BlockPos(start.add(direction.scale(distance)));
                if (!player.world.isBlockLoaded(pos)) { range = Math.max(0, distance - 1); break; }
            }
            RayTraceResult hit = player.world.rayTraceBlocks(start, start.add(direction.scale(range)), false, true, false);
            double stop = hit == null ? range : start.distanceTo(hit.hitVec);
            List<Integer> cells = new ArrayList<>();
            int previous = -1;
            for (double distance = .25; distance < stop; distance += .5) {
                BlockPos pos = new BlockPos(start.add(direction.scale(distance)));
                if (!inside(pos, minX, minY, minZ, maxX, maxY, maxZ)) continue;
                int cell = cellIndex(pos, baseX, baseY, baseZ, sizeY, sizeZ, quest.horizontalCoverage);
                if (cell != previous) cells.add(cell);
                previous = cell;
            }
            if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                    && inside(hit.getBlockPos(), minX, minY, minZ, maxX, maxY, maxZ)) {
                int cell = cellIndex(hit.getBlockPos(), baseX, baseY, baseZ, sizeY, sizeZ, quest.horizontalCoverage);
                if (cell != previous) cells.add(cell);
            }
            rays.add(cells.stream().mapToInt(Integer::intValue).toArray());
        }
        final long token = ++tokenCounter;
        inFlight = new Ticket(token, player, pdata, data, quest, dimension);
        final int[][] snapshot = rays.toArray(new int[rays.size()][]);
        // Only integer snapshots cross the worker boundary; no world/player/quest references.
        WORKER.execute(() -> COMPLETED.add(new Completion(token, visibleCells(snapshot, (int)total))));
    }

    /** At least 2/15 independent ray samples must cover a cell (13.3% of the samples). */
    public static int[] visibleCells(int[][] rays, int total) {
        int[] samples = Arrays.stream(rays).flatMapToInt(Arrays::stream).filter(cell -> cell >= 0 && cell < total).sorted().toArray();
        int[] result = new int[samples.length / 2];
        int count = 0;
        for (int i = 0; i < samples.length;) {
            int end = i + 1;
            while (end < samples.length && samples[end] == samples[i]) end++;
            if (end - i >= 2) result[count++] = samples[i];
            i = end;
        }
        return Arrays.copyOf(result, count);
    }

    private static boolean inside(BlockPos pos, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    private static int cellIndex(BlockPos pos, int baseX, int baseY, int baseZ, int sizeY, int sizeZ, boolean horizontal) {
        int x = Math.floorDiv(pos.getX(), 4) - baseX;
        int y = Math.floorDiv(pos.getY(), 4) - baseY;
        int z = Math.floorDiv(pos.getZ(), 4) - baseZ;
        return horizontal ? x * sizeZ + z : (x * sizeY + y) * sizeZ + z;
    }

    private static void finish(MinecraftServer server) {
        Completion completion;
        while ((completion = COMPLETED.poll()) != null) {
            Ticket ticket = inFlight;
            if (ticket == null || completion.token != ticket.token) continue;
            inFlight = null;
            EntityPlayer online = server.getPlayerList().getPlayerByUUID(ticket.player.getUniqueID());
            if (online != ticket.player || ticket.player.world.provider.getDimension() != ticket.dimension
                    || ticket.playerData.questData.activeQuests.get(ticket.data.quest.id) != ticket.data
                    || ticket.data.quest.questInterface != ticket.quest
                    || ticket.quest.horizontalCoverage != ticket.horizontalCoverage) continue;
            if (!QuestExplore.dimensionMatches(ticket.data, ticket.dimension)) continue;
            long total = ticket.quest.cellCount();
            if (total <= 0) continue;
            BitSet explored = BitSet.valueOf(ticket.data.extraData.getByteArray(QuestExplore.CELLS));
            boolean modeChanged = !ticket.quest.progressModeMatches(ticket.data.extraData);
            if (modeChanged) explored.clear();
            int previous = explored.cardinality();
            for (int cell : completion.cells) explored.set(cell);
            if (!modeChanged && explored.cardinality() == previous) continue;
            ticket.data.extraData.setBoolean(QuestExplore.COVERAGE_MODE_KEY, ticket.horizontalCoverage);
            ticket.data.extraData.setString(QuestExplore.DIMENSION, Integer.toString(ticket.dimension));
            ticket.data.extraData.setByteArray(QuestExplore.CELLS, explored.toByteArray());
            ticket.playerData.updateClient = true;
            ticket.playerData.questData.checkQuestCompletion(ticket.player, 7);
        }
    }

    public static void reset() {
        inFlight = null;
        tokenCounter++;
        scanCursor = 0;
        COMPLETED.clear();
    }
}
