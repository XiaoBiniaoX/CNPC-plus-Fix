package bin.cnpcplus.quest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.common.FMLCommonHandler;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestInterface;

/** Region definition belongs to the quest; discovered cells belong to an active quest record. */
public final class QuestExplore extends QuestInterface {
    public static final int MAX_CELLS = 1000000;
    private static final String CELLS = "CNPCPlusExploreCells";
    private static final String DIMENSION = "CNPCPlusExploreDimension";
    public int x1, y1, z1, x2, y2, z2;
    public int requiredPercent = 50;
    public int viewDistance = 16;
    public boolean boundsSet;

    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.x1 = x1; this.y1 = y1; this.z1 = z1;
        this.x2 = x2; this.y2 = y2; this.z2 = z2;
        boundsSet = true;
        normalize();
    }

    public void normalize() {
        int value = Math.min(x1, x2); x2 = Math.max(x1, x2); x1 = value;
        value = Math.min(y1, y2); y2 = Math.max(y1, y2); y1 = value;
        value = Math.min(z1, z2); z2 = Math.max(z1, z2); z1 = value;
        requiredPercent = Math.max(1, Math.min(80, requiredPercent));
        viewDistance = Math.max(1, Math.min(128, viewDistance));
    }

    public long cellCount() {
        if (!boundsSet) return 0;
        normalize();
        int minY = Math.max(0, y1), maxY = Math.min(255, y2);
        if (maxY < minY) return 0;
        long sx = Math.floorDiv((long)x2, 4) - Math.floorDiv((long)x1, 4) + 1;
        long sy = Math.floorDiv((long)maxY, 4) - Math.floorDiv((long)minY, 4) + 1;
        long sz = Math.floorDiv((long)z2, 4) - Math.floorDiv((long)z1, 4) + 1;
        if (sx <= 0 || sy <= 0 || sz <= 0 || sx > MAX_CELLS / sy || sx * sy > MAX_CELLS / sz) return -1;
        return sx * sy * sz;
    }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        x1 = tag.getInteger("CNPCPlusExploreX1"); y1 = tag.getInteger("CNPCPlusExploreY1"); z1 = tag.getInteger("CNPCPlusExploreZ1");
        x2 = tag.getInteger("CNPCPlusExploreX2"); y2 = tag.getInteger("CNPCPlusExploreY2"); z2 = tag.getInteger("CNPCPlusExploreZ2");
        requiredPercent = tag.hasKey("CNPCPlusExplorePercent") ? tag.getInteger("CNPCPlusExplorePercent") : 50;
        viewDistance = tag.hasKey("CNPCPlusExploreDistance") ? tag.getInteger("CNPCPlusExploreDistance") : 16;
        boundsSet = tag.getBoolean("CNPCPlusExploreBoundsSet");
        normalize();
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        normalize();
        tag.setInteger("CNPCPlusExploreX1", x1); tag.setInteger("CNPCPlusExploreY1", y1); tag.setInteger("CNPCPlusExploreZ1", z1);
        tag.setInteger("CNPCPlusExploreX2", x2); tag.setInteger("CNPCPlusExploreY2", y2); tag.setInteger("CNPCPlusExploreZ2", z2);
        tag.setInteger("CNPCPlusExplorePercent", requiredPercent); tag.setInteger("CNPCPlusExploreDistance", viewDistance);
        tag.setBoolean("CNPCPlusExploreBoundsSet", boundsSet);
    }

    @Override public boolean isCompleted(EntityPlayer player) {
        QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
        if (data == null || !dimensionMatches(data, player.world.provider.getDimension())) return false;
        long total = cellCount();
        return total > 0 && explored(data, total) * 100L >= total * requiredPercent;
    }

    @Override public void handleComplete(EntityPlayer player) {}

    @Override public IQuestObjective[] getObjectives(EntityPlayer player) {
        return new IQuestObjective[]{new ExploreObjective(player)};
    }

    private int explored(QuestData data, long total) {
        java.util.BitSet cells = java.util.BitSet.valueOf(data.extraData.getByteArray(CELLS));
        return (int)Math.min(total, cells.cardinality());
    }

    private final class ExploreObjective implements IQuestObjective {
        private final EntityPlayer player;
        private ExploreObjective(EntityPlayer player) { this.player = player; }
        private double percent() {
            QuestData data = PlayerData.get(player).questData.activeQuests.get(questId);
            if (data == null || !dimensionMatches(data, player.world.provider.getDimension())) return 0;
            long total = cellCount();
            return total <= 0 ? 0 : explored(data, total) * 100.0D / total;
        }
        @Override public int getProgress() { return (int)percent(); }
        @Override public void setProgress(int value) {
            if (value < 0 || value > 100) throw new CustomNPCsException("Progress has to be between 0 and 100");
        }
        @Override public int getMaxProgress() { return 100; }
        @Override public boolean isCompleted() { return percent() >= requiredPercent; }
        @Override public String getText() {
            return I18n.translateToLocal("cnpcplus.quest.explore.progress") + ": "
                    + String.format(java.util.Locale.ROOT, "%.1f%%", percent());
        }
    }

    private static boolean dimensionMatches(QuestData data, int dimension) {
        String saved = data.extraData.getString(DIMENSION);
        return saved.isEmpty() || saved.equals(Integer.toString(dimension));
    }

    public static final class RegionRecorder {
        private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "cnpcplus-quest-explore");
            thread.setDaemon(true);
            return thread;
        });
        private static final java.util.concurrent.ConcurrentLinkedQueue<Completion> COMPLETED =
                new java.util.concurrent.ConcurrentLinkedQueue<Completion>();
        private static long scanCursor, tokenCounter;
        private static Ticket inFlight;

        private static final class Ticket {
            final long token;
            final EntityPlayer player;
            final PlayerData playerData;
            final QuestData data;
            final QuestExplore quest;
            final int dimension;
            Ticket(long token, EntityPlayer player, PlayerData playerData, QuestData data, QuestExplore quest, int dimension) {
                this.token = token; this.player = player; this.playerData = playerData;
                this.data = data; this.quest = quest; this.dimension = dimension;
            }
        }
        private static final class Completion {
            final long token;
            final int[] cells;
            final Throwable failure;
            Completion(long token, int[] cells, Throwable failure) { this.token = token; this.cells = cells; this.failure = failure; }
        }

        private RegionRecorder() {}

        public static void tick(net.minecraft.server.MinecraftServer server) {
            finish(server);
            if (inFlight != null || server == null) return;
            List<EntityPlayer> players = new ArrayList<EntityPlayer>(server.getPlayerList().getPlayers());
            if (players.isEmpty()) { scanCursor = 0; return; }
            long cursor = scanCursor++;
            EntityPlayer player = players.get((int)(cursor % players.size()));
            PlayerData pdata = PlayerData.get(player);
            List<QuestData> quests = new ArrayList<QuestData>();
            for (QuestData data : pdata.questData.activeQuests.values())
                if (data.quest.type == 7 && data.quest.questInterface instanceof QuestExplore) quests.add(data);
            if (quests.isEmpty()) return;
            QuestData data = quests.get((int)((cursor / players.size()) % quests.size()));
            submit(player, pdata, data, (QuestExplore)data.quest.questInterface);
        }

        private static void submit(EntityPlayer player, PlayerData pdata, QuestData data, QuestExplore quest) {
            long total = quest.cellCount();
            if (total <= 0 || total > MAX_CELLS) return;
            int dimension = player.world.provider.getDimension();
            if (!dimensionMatches(data, dimension)) return;
            int minX = quest.x1, maxX = quest.x2;
            int minY = Math.max(0, quest.y1), maxY = Math.min(255, quest.y2);
            int minZ = quest.z1, maxZ = quest.z2;
            if (maxY < minY) return;
            int baseX = Math.floorDiv(minX, 4), baseY = Math.floorDiv(minY, 4), baseZ = Math.floorDiv(minZ, 4);
            int sizeY = Math.floorDiv(maxY, 4) - baseY + 1, sizeZ = Math.floorDiv(maxZ, 4) - baseZ + 1;
            Vec3d look = player.getLookVec();
            double yaw = Math.toRadians(player.rotationYaw);
            Vec3d right = new Vec3d(-Math.sin(yaw), 0.0D, Math.cos(yaw));
            Vec3d up = right.crossProduct(look).normalize();
            Vec3d start = player.getPositionEyes(1.0F);
            List<int[]> rays = new ArrayList<int[]>(15);
            for (int yi = -1; yi <= 1; yi++) for (int xi = -2; xi <= 2; xi++) {
                Vec3d direction = look.add(right.scale(xi * 0.28D)).add(up.scale(yi * 0.32D)).normalize();
                Vec3d end = start.add(direction.scale(quest.viewDistance));
                RayTraceResult hit = player.world.rayTraceBlocks(start, end, false, true, false);
                double stop = hit == null ? quest.viewDistance : start.distanceTo(hit.hitVec);
                java.util.BitSet rayCells = new java.util.BitSet((int)total);
                for (double distance = 1.0D; distance < stop; distance += 1.25D) {
                    BlockPos pos = new BlockPos(start.x + direction.x * distance,
                            start.y + direction.y * distance, start.z + direction.z * distance);
                    if (inside(pos, minX, minY, minZ, maxX, maxY, maxZ))
                        rayCells.set(cellIndex(pos, baseX, baseY, baseZ, sizeY, sizeZ));
                }
                if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK
                        && inside(hit.getBlockPos(), minX, minY, minZ, maxX, maxY, maxZ))
                    rayCells.set(cellIndex(hit.getBlockPos(), baseX, baseY, baseZ, sizeY, sizeZ));
                rays.add(rayCells.stream().toArray());
            }
            final long token = ++tokenCounter;
            inFlight = new Ticket(token, player, pdata, data, quest, dimension);
            final int[][] snapshot = rays.toArray(new int[rays.size()][]);
            WORKER.execute(() -> {
                try { COMPLETED.add(new Completion(token, visibleCells(snapshot, (int)total), null)); }
                catch (Throwable failure) { COMPLETED.add(new Completion(token, null, failure)); }
            });
        }

        public static int[] visibleCells(int[][] rays, int total) {
            byte[] hits = new byte[total];
            for (int[] ray : rays) for (int cell : ray) if (cell >= 0 && cell < total && hits[cell] < 2) hits[cell]++;
            int size = 0;
            for (byte hit : hits) if (hit >= 2) size++;
            int[] result = new int[size];
            for (int i = 0, out = 0; i < hits.length; i++) if (hits[i] >= 2) result[out++] = i;
            return result;
        }

        private static boolean inside(BlockPos pos, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        private static int cellIndex(BlockPos pos, int baseX, int baseY, int baseZ, int sizeY, int sizeZ) {
            int x = Math.floorDiv(pos.getX(), 4) - baseX;
            int y = Math.floorDiv(pos.getY(), 4) - baseY;
            int z = Math.floorDiv(pos.getZ(), 4) - baseZ;
            return (x * sizeY + y) * sizeZ + z;
        }

        private static void finish(net.minecraft.server.MinecraftServer server) {
            Completion completion;
            while ((completion = COMPLETED.poll()) != null) {
                Ticket ticket = inFlight;
                if (ticket == null || completion.token != ticket.token) continue;
                inFlight = null;
                if (completion.failure != null) {
                    bin.cnpcplus.CnpcPlus.LOGGER.error("Quest exploration aggregation failed", completion.failure);
                    continue;
                }
                EntityPlayer online = server.getPlayerList().getPlayerByUUID(ticket.player.getUniqueID());
                if (online != ticket.player || ticket.player.world.provider.getDimension() != ticket.dimension
                        || ticket.playerData.questData.activeQuests.get(ticket.data.quest.id) != ticket.data
                        || ticket.data.quest.questInterface != ticket.quest) continue;
                String dimension = Integer.toString(ticket.dimension);
                String savedDimension = ticket.data.extraData.getString(DIMENSION);
                if (!savedDimension.isEmpty() && !savedDimension.equals(dimension)) continue;
                long total = ticket.quest.cellCount();
                if (total <= 0 || total > MAX_CELLS) continue;
                java.util.BitSet explored = java.util.BitSet.valueOf(ticket.data.extraData.getByteArray(CELLS));
                int previous = explored.cardinality();
                for (int cell : completion.cells) if (cell >= 0 && cell < total) explored.set(cell);
                if (explored.cardinality() == previous) continue;
                if (savedDimension.isEmpty()) ticket.data.extraData.setString(DIMENSION, dimension);
                ticket.data.extraData.setByteArray(CELLS, explored.toByteArray());
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
}
