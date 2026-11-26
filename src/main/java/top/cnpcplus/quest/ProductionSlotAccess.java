package top.cnpcplus.quest;

import net.minecraft.world.item.ItemStack;

/** Keeps the actual quick-move amount until the output's successful onTake callback. */
public interface ProductionSlotAccess {
    ItemStack cnpcplus$takeProduction(ItemStack taken);
}
