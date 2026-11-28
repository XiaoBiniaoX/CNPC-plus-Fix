package bin.cnpcplus.quest;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.item.ItemStack;
import noppes.npcs.containers.ContainerCarpentryBench;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.QuestData;

public final class QuestProductionProgress {
    public static void crafted(EntityPlayer player, InventoryCrafting matrix, ItemStack stack, int count) {
        if (player.world.isRemote || count <= 0) return;
        CraftStation station;
        String recipeId;
        if (player.openContainer instanceof ContainerCarpentryBench) {
            RecipeCarpentry recipe = RecipeController.instance.findMatchingRecipe(matrix);
            if (recipe == null) return;
            station = CraftStation.CARPENTRY;
            recipeId = Integer.toString(recipe.id);
            if (stack.isEmpty()) stack = recipe.getCraftingResult(matrix);
        } else if (player.openContainer instanceof ContainerWorkbench) {
            IRecipe recipe = CraftingManager.findMatchingRecipe(matrix, player.world);
            if (recipe == null) return;
            station = CraftStation.WORKBENCH;
            if (!(recipe instanceof RecipeCarpentry)) return;
            recipeId = Integer.toString(((RecipeCarpentry)recipe).id);
            if (stack.isEmpty()) stack = recipe.getCraftingResult(matrix);
        } else return;
        ItemProductionEvents.post(player, stack, station,
                ((CraftingStationPosition)player.openContainer).cnpcplus$craftingPos(), count, recipeId);
    }

    @SubscribeEvent
    public void onProduction(ItemProductionEvent event) {
        EntityPlayer player = event.getPlayer();
        if (event.getWorld().isRemote || player.world != event.getWorld() || event.getCount() <= 0) return;
        PlayerData pdata = PlayerData.get(player);
        boolean changed = false;
        for (QuestData data : pdata.questData.activeQuests.values()) {
            if (data.quest.type != 6 || !(data.quest.questInterface instanceof QuestCraft)) continue;
            QuestCraft quest = (QuestCraft)data.quest.questInterface;
            for (int i = 0; i < 9; i++) {
                if (!quest.configured(i) || !quest.stations[i].equals(event.getStation().key)
                        || !quest.recipeIds[i].equals(event.getRecipeId())
                        || !QuestCraft.matches(quest.items.items.get(i), event.getStack())) continue;
                int[] counts = quest.progress(data);
                int current = i < counts.length ? counts[i] : 0;
                int required = quest.requiredCount(i);
                if (current >= required) continue;
                quest.addProgress(data, i, Math.min(event.getCount(), required - current));
                changed = true;
            }
        }
        if (changed) {
            pdata.updateClient = true;
            pdata.questData.checkQuestCompletion(player, 6);
        }
    }
}
