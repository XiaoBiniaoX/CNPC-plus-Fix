package top.cnpcplus.quest;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerQuestData;
import noppes.npcs.controllers.data.QuestData;

/** Server-side quest progress event handlers. */
public final class QuestProgression {
    private QuestProgression() {}

    @SubscribeEvent
    public static void production(ItemProductionEvent event) {
        if (event.getLevel().isClientSide || event.getPlayer().level() != event.getLevel()) return;
        PlayerData pdata = PlayerData.get(event.getPlayer());
        PlayerQuestData quests = pdata.questData;
        for (QuestData data : java.util.List.copyOf(quests.activeQuests.values())) {
            if (data.quest.type != 6 || !(data.quest.questInterface instanceof QuestCraft craft)) continue;
            for (int i = 0; i < 9; i++) {
                if (!craft.configured(i) || !QuestCraft.matchesRecipe(craft.stations[i], craft.recipeIds[i],
                        event.getStation(), event.getRecipeId())) continue;
                int[] counts = craft.progress(data);
                int current = i < counts.length ? counts[i] : 0;
                if (current >= craft.required[i]) continue;
                craft.addProgress(data, i, Math.min(event.getCount(), craft.required[i] - current));
                quests.checkQuestCompletion(event.getPlayer(), 6);
                pdata.updateClient = true;
            }
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        QuestRegionRecorder.tick(event.getServer());
    }

}
