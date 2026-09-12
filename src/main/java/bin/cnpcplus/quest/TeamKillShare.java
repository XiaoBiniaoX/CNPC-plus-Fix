package bin.cnpcplus.quest;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerQuestData;
import noppes.npcs.controllers.data.QuestData;
import noppes.npcs.quests.QuestKill;

import java.util.HashMap;

/**
 * 队伍内击杀任务进度同步。
 *
 * <p>需求：任务接取者用指令给自己加了 team，队友也在同一 team 内时，队友的击杀也计入接取者的
 * 击杀任务进度（A 有任务，B/C 没有，B/C 击杀目标后 A 同样能完成）。
 *
 * <p>「谁在队伍里」完全由原版记分板说话（{@code Entity.getTeam()}），玩家 {@code /team leave}
 * 之后下一次击杀就自动不再共享，不需要我们做任何清理，也不落 CNPC 存档。
 *
 * <p>刻意放在 mixin 包之外：mixin 包内的类不能被外部直接引用。
 */
public final class TeamKillShare {

    private TeamKillShare() {
    }

    /**
     * 把一次击杀扩散给击杀者的在线队友。
     *
     * <p>与 CNPC 自己的记账逻辑（ServerEventsHandler.doQuest:205-218）保持一致：
     * 目标名优先用实体显示名（自定义 NPC 会改名），否则用注册表 ID；命中上限则跳过。
     *
     * @param killer     实际击杀者
     * @param entity     被击杀的实体
     * @param entityName CNPC 算出的注册表名（玩家统一为 "Player"）
     */
    public static void share(Player killer, LivingEntity entity, String entityName) {
        if (killer == null || entity == null || entityName == null) return;
        if (killer.level() == null || killer.level().isClientSide) return;

        PlayerTeam team = killer.getTeam();
        if (team == null) return;

        MinecraftServer server = killer.getServer();
        if (server == null || server.getPlayerList() == null) return;

        String displayName = entity.getName() == null ? null : entity.getName().getString();

        for (ServerPlayer mate : server.getPlayerList().getPlayers()) {
            if (mate == null || mate == killer) continue;
            // 引用相等即同队，与 CNPC 自己在 NpcNearestAttackableTargetGoal 里的写法一致。
            if (mate.getTeam() != team) continue;
            addKill(mate, entityName, displayName);
        }
    }

    /**
     * 给一名队友的进行中击杀任务加一次进度。
     *
     * <p>只遍历 {@code activeQuests}，所以没接过该任务的队友这里什么都不会发生 ——
     * 绝不会凭空替人接任务。非击杀类任务（物品搜集/对话/地点/手动）由类型判断排除。
     */
    private static void addKill(ServerPlayer mate, String entityName, String displayName) {
        PlayerData pdata = PlayerData.get(mate);
        if (pdata == null || pdata.questData == null) return;
        PlayerQuestData questData = pdata.questData;

        boolean changed = false;
        for (QuestData data : questData.activeQuests.values()) {
            if (data == null || data.quest == null) continue;
            // 2 = KILL，4 = AREA_KILL，其余任务类型一律不同步。
            if (data.quest.type != 2 && data.quest.type != 4) continue;
            if (!(data.quest.questInterface instanceof QuestKill quest)) continue;

            String name = entityName;
            if (displayName != null && quest.targets.containsKey(displayName)) {
                name = displayName;
            } else if (!quest.targets.containsKey(name)) {
                continue;
            }

            Integer target = quest.targets.get(name);
            if (target == null) continue;

            HashMap<String, Integer> killed = quest.getKilled(data);
            int amount = killed.containsKey(name) ? killed.get(name) : 0;
            // 已达上限就不再加，避免超额。
            if (amount >= target) continue;

            killed.put(name, amount + 1);
            quest.setKilled(data, killed);
            changed = true;
        }

        if (!changed) return;

        // 完成判定与客户端刷新都必须补上，否则队友进度只在存档里涨、界面和完成态都不动。
        questData.checkQuestCompletion(mate, 2);
        questData.checkQuestCompletion(mate, 4);
        pdata.updateClient = true;
    }
}
