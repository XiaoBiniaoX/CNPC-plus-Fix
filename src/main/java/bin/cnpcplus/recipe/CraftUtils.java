package bin.cnpcplus.recipe;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of 1.20.1 cnpcplus CraftUtils matching rules.
 * Replaces vanilla meaning of recipe.ignoreDamage / recipe.ignoreNBT:
 * - ignoreNBT=true  -> name-only (same item id + same hover name)
 * - ignoreDamage=true -> config fuzzy (item id + hover name + configured NBT string fields)
 * - both false -> exact (item + damage + full components when present)
 */
public final class CraftUtils {
    private CraftUtils() {}

    public static boolean matches(ItemStack playerStack, ItemStack required,
                                  boolean ignoreDamage, boolean ignoreNBT) {
        if (playerStack == null || required == null) return false;
        if (playerStack.isEmpty() || required.isEmpty()) return false;
        if (playerStack.getItem() != required.getItem()) return false;

        // 仅名字检查
        if (ignoreNBT) {
            return sameName(playerStack, required);
        }

        // 配置模糊化
        if (ignoreDamage) {
            if (!sameName(playerStack, required)) return false;
            Map<String, List<String>> rules = getRecipeFuzzyRules();
            String itemId = BuiltInRegistries.ITEM.getKey(required.getItem()).toString();
            List<String> fields = rules.get(itemId);
            if (fields == null || fields.isEmpty()) {
                return true;
            }
            CompoundTag tagPlayer = customTag(playerStack);
            CompoundTag tagRequired = customTag(required);
            if (tagPlayer == null || tagRequired == null) return false;
            for (String field : fields) {
                if (field == null || field.isEmpty()) continue;
                if (!tagPlayer.getString(field).equals(tagRequired.getString(field))) {
                    return false;
                }
            }
            return true;
        }

        // 精确比对
        if (playerStack.getDamageValue() != required.getDamageValue()) return false;
        if (!required.getComponents().isEmpty()) {
            if (playerStack.getComponents().isEmpty()) return false;
            if (!playerStack.getComponents().equals(required.getComponents())) return false;
        }
        return true;
    }

    private static boolean sameName(ItemStack a, ItemStack b) {
        return a.getHoverName().getString().equals(b.getHoverName().getString());
    }

    private static CompoundTag customTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return null;
        return data.copyTag();
    }

    /**
     * Format: itemId|field1,field2;itemId2|fieldA
     * Default includes TACZ + slashblade like 1.20.1.
     */
    public static Map<String, List<String>> getRecipeFuzzyRules() {
        String raw = CnpcPlusConfig.RECIPE_FUZZY_MATCH_RULES.get();
        Map<String, List<String>> rules = new HashMap<>();
        if (raw == null || raw.isBlank()) return rules;
        for (String part : raw.split(";")) {
            if (part == null || part.isBlank()) continue;
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