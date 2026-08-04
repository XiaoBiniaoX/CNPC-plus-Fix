package top.cnpcplus.craftingview;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import top.cnpcplus.config.CnpcPlusConfigData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品匹配工具
 *
 * 匹配规则由传入的 ignoreDamage / ignoreNBT 决定：
 * - ignoreNBT = true  → 仅名字检查（物品 ID + 显示名称）
 * - ignoreDamage = true → 配置模糊化（物品 ID + 显示名称 + 配置 NBT 字段）
 * - 两者都 false  → 精确比对（物品 ID + 耐久 + 完整 NBT）
 */
public class CraftUtils {

    public static boolean matches(ItemStack playerStack, ItemStack required,
                                  boolean ignoreDamage, boolean ignoreNBT) {
        if (playerStack.getItem() != required.getItem()) return false;

        // 仅名字检查：只允许同一个物品 ID，并要求显示名称一致。
        if (ignoreNBT) {
            return sameName(playerStack, required);
        }

        // 配置模糊化：跳过耐久，只额外检查配置里指定的 NBT 字段。
        if (ignoreDamage) {
            Map<String, List<String>> rules = getRecipeFuzzyRules();
            String itemId = BuiltInRegistries.ITEM.getKey(required.getItem()).toString();
            List<String> fields = rules.get(itemId);

            if (!sameName(playerStack, required)) return false;

            // 检查配置的 NBT 字段
            if (fields != null) {
                for (String field : fields) {
                    if (field.isEmpty()) continue;
                    CompoundTag tag1 = playerStack.getTag();
                    CompoundTag tag2 = required.getTag();
                    if (tag1 == null || tag2 == null) return false;
                    if (!tag1.getString(field).equals(tag2.getString(field))) return false;
                }
            }
            return true;
        }

        // 精确比对
        if (playerStack.getDamageValue() != required.getDamageValue()) return false;
        if (required.hasTag()) {
            if (!playerStack.hasTag()) return false;
            if (!playerStack.getTag().equals(required.getTag())) return false;
        }
        return true;
    }

    private static boolean sameName(ItemStack playerStack, ItemStack required) {
        return playerStack.getHoverName().getString().equals(required.getHoverName().getString());
    }

    private static Map<String, List<String>> getRecipeFuzzyRules() {
        String raw = CnpcPlusConfigData.RecipeFuzzyMatchRules.get();
        Map<String, List<String>> rules = new HashMap<>();
        if (raw == null || raw.isBlank()) return rules;
        for (String part : raw.split(";")) {
            if (part.isBlank()) continue;
            String[] split = part.split("\\|", 2);
            if (split.length == 0 || split[0].isBlank()) continue;
            List<String> fields = new ArrayList<>();
            if (split.length == 2 && !split[1].isBlank()) {
                for (String field : split[1].split(",")) {
                    String trimmed = field.trim();
                    if (!trimmed.isEmpty()) fields.add(trimmed);
                }
            }
            rules.put(split[0].trim(), fields);
        }
        return rules;
    }
}
