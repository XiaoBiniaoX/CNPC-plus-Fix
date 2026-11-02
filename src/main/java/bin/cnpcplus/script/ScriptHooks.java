package bin.cnpcplus.script;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import noppes.npcs.constants.EnumScriptType;
import noppes.npcs.controllers.ScriptContainer;

/** Legacy CNPC has no hook registry. Keep native carrier hooks plus actual addon dispatch names. */
public final class ScriptHooks {
    private static final Map<ScriptContainer, Set<String>> OBSERVED = new WeakHashMap<>(), GLOBALS = new WeakHashMap<>();
    private ScriptHooks() { }
    public static void observe(ScriptContainer owner, String name, Object event) {
        if (event != null && name.matches("[$_\\p{L}][$_\\p{L}\\p{N}\\p{M}]*"))
            OBSERVED.computeIfAbsent(owner, unused -> new TreeSet<>()).add(name);
    }
    public static Set<String> globals(ScriptContainer owner) { return GLOBALS.getOrDefault(owner, Collections.emptySet()); }
    public static List<String> names(ScriptContainer owner, String context) {
        Set<String> names = new TreeSet<>(OBSERVED.getOrDefault(owner, Collections.emptySet()));
        String nativeTypes;
        switch (context) {
            case "NpcEvent": nativeTypes = "INIT TICK INTERACT DIALOG DAMAGED DIED ATTACK_MELEE TARGET COLLIDE KILL DIALOG_OPTION TARGET_LOST ROLE RANGED_LAUNCHED TIMER DIALOG_CLOSE PROJECTILE_TICK PROJECTILE_IMPACT CUSTOM_GUI_CLOSED CUSTOM_GUI_BUTTON CUSTOM_GUI_SLOT CUSTOM_GUI_SCROLL CUSTOM_CHEST_CLOSED CUSTOM_CHEST_CLICKED"; break;
            case "BlockDoorEvent":
            case "BlockEvent": nativeTypes = "INIT TICK INTERACT COLLIDE CLICKED FALLEN_UPON RAIN_FILLED BROKEN HARVESTED EXPLODED NEIGHBOR_CHANGED REDSTONE TIMER" + (context.equals("BlockDoorEvent") ? " DOOR_TOGGLE" : ""); break;
            case "ItemEvent": nativeTypes = "INIT TICK TOSSED PICKEDUP SPAWN INTERACT ATTACK PROJECTILE_TICK PROJECTILE_IMPACT"; break;
            case "PlayerEvent": nativeTypes = "INIT TICK INTERACT ATTACK BROKEN TOSS LEVEL_UP PICKUP CONTAINER_OPEN CONTAINER_CLOSED DIED KILL TIMER DAMAGED LOGIN LOGOUT CHAT RANGED_LAUNCHED DAMAGED_ENTITY FACTION_UPDATE KEY_PRESSED DIALOG DIALOG_OPTION DIALOG_CLOSE QUEST_START QUEST_COMPLETED QUEST_TURNIN SCRIPT_COMMAND PROJECTILE_TICK PROJECTILE_IMPACT CUSTOM_GUI_CLOSED CUSTOM_GUI_BUTTON CUSTOM_GUI_SLOT CUSTOM_GUI_SCROLL CUSTOM_CHEST_CLOSED CUSTOM_CHEST_CLICKED"; break;
            default: nativeTypes = ""; // Forge names derive from actual event class names, not CNPC's enum.
        }
        for (String type : nativeTypes.split(" ")) if (!type.isEmpty()) names.add(EnumScriptType.valueOf(type).function);
        return new ArrayList<>(names);
    }
    public static void read(ScriptContainer owner, NBTTagCompound tag) {
        for (String key : new String[]{"CNPCPlusScriptHooks", "CNPCPlusScriptGlobals"}) {
            Set<String> values = new TreeSet<>(); NBTTagList list = tag.getTagList(key, 8);
            for (int i = 0; i < list.tagCount(); i++) values.add(list.getStringTagAt(i));
            (key.endsWith("Hooks") ? OBSERVED : GLOBALS).put(owner, values);
        }
    }
    public static void write(ScriptContainer owner, NBTTagCompound tag, Set<String> globals) {
        GLOBALS.put(owner, new TreeSet<>(globals));
        for (String key : new String[]{"CNPCPlusScriptHooks", "CNPCPlusScriptGlobals"}) {
            NBTTagList list = new NBTTagList();
            for (String value : (key.endsWith("Hooks") ? OBSERVED : GLOBALS).getOrDefault(owner, Collections.emptySet())) list.appendTag(new NBTTagString(value));
            tag.setTag(key, list);
        }
    }
}
