package bin.cnpcplus.quest;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;

public final class QuestProductionProgress {
    @SubscribeEvent
    public void onProduction(ItemProductionEvent event) {
        EntityPlayer player = event.getPlayer();
        if (event.getWorld().isRemote || player.world != event.getWorld() || event.getCount() <= 0) return;
        PlayerData pdata = PlayerData.get(player);
        for (QuestData data : pdata.questData.activeQuests.values()) {
            if (data.quest.type != 6 || !(data.quest.questInterface instanceof QuestCraft)) continue;
            QuestCraft quest = (QuestCraft)data.quest.questInterface;
            for (int i = 0; i < 9; i++) {
                if (!quest.configured(i) || !quest.stations[i].equals(event.getStation().key)
                        || !quest.recipeIds[i].equals(event.getRecipeId())
                        || !QuestCraft.matches(quest.items.items.get(i), event.getStack())) continue;
                int[] counts = quest.progress(data);
                int current = i < counts.length ? counts[i] : 0;
                int required = quest.items.items.get(i).getCount();
                if (current >= required) continue;
                quest.addProgress(data, i, Math.min(event.getCount(), required - current));
                pdata.questData.checkQuestCompletion(player, 6);
                pdata.updateClient = true;
            }
        }
    }
}
