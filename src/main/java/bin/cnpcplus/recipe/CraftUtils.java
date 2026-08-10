package bin.cnpcplus.recipe;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Matching rules ported from 1.20.1/1.21.1 cnpcplus:
 * - ignoreNBT=true  -> name-only (same item + same display name)
 * - ignoreDamage=true -> config fuzzy (item + name + configured NBT string fields)
 * - both false -> exact (item + damage + full NBT)
 */
public final class CraftUtils {
    private CraftUtils() {}

    public static boolean matches(ItemStack playerStack, ItemStack required,
                                  boolean ignoreDamage, boolean ignoreNBT) {
        if (playerStack == null || required == null) return false;
        if (playerStack.isEmpty() || required.isEmpty()) return false;
        if (playerStack.getItem() != required.getItem()) return false;

        if (ignoreNBT) {
            return sameName(playerStack, required);
        }

        if (ignoreDamage) {
            if (!sameName(playerStack, required)) return false;
            Map<String, List<String>> rules = getRecipeFuzzyRules();
            String itemId = itemId(required);
            List<String> fields = rules.get(itemId);
            if (fields == null || fields.isEmpty()) {
                return true;
            }
            NBTTagCompound tagPlayer = playerStack.getTagCompound();
            NBTTagCompound tagRequired = required.getTagCompound();
            if (tagPlayer == null || tagRequired == null) return false;
            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                if (field == null || field.isEmpty()) continue;
                if (!tagPlayer.getString(field).equals(tagRequired.getString(field))) {
                    return false;
                }
            }
            return true;
        }

if (playerStack.getItemDamage() != required.getItemDamage()) return false;
        NBTTagCompound a = playerStack.getTagCompound();
        NBTTagCompound b = required.getTagCompound();
        if (b != null && !b.getKeySet().isEmpty()) {
            if (a == null || a.getKeySet().isEmpty()) return false;
            if (!a.equals(b)) return false;
        }
        return true;
    }

    private static boolean sameName(ItemStack a, ItemStack b) {
        String na = a.getDisplayName();
        String nb = b.getDisplayName();
        if (na == null) na = "";
        if (nb == null) nb = "";
        return na.equals(nb);
    }

    private static String itemId(ItemStack stack) {
        ResourceLocation loc = Item.REGISTRY.getNameForObject(stack.getItem());
        return loc != null ? loc.toString() : "";
    }

    public static Map<String, List<String>> getRecipeFuzzyRules() {
        String raw = CnpcPlusConfig.getRecipeFuzzyMatchRules();
        Map<String, List<String>> rules = new HashMap<String, List<String>>();
        if (raw == null || raw.trim().isEmpty()) return rules;
        String[] parts = raw.split(";");
        for (int p = 0; p < parts.length; p++) {
            String part = parts[p];
            if (part == null || part.trim().isEmpty()) continue;
            String[] split = part.split("\\|", 2);
            if (split.length == 0 || split[0].trim().isEmpty()) continue;
            List<String> fields = new ArrayList<String>();
            if (split.length == 2 && !split[1].trim().isEmpty()) {
                String[] fs = split[1].split(",");
                for (int i = 0; i < fs.length; i++) {
                    String trimmed = fs[i].trim();
                    if (!trimmed.isEmpty()) fields.add(trimmed);
                }
            }
            rules.put(split[0].trim(), fields);
        }
        return rules;
    }
}
