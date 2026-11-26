package top.cnpcplus.api.script;

import bin.cnpcplus.script.ApiCompletion;
import java.util.*;
import noppes.npcs.constants.EnumScriptType;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.data.PlayerScriptData;
import noppes.npcs.controllers.data.ForgeScriptData;
import noppes.npcs.entity.data.DataScript;
import noppes.npcs.blocks.tiles.TileScripted;
import noppes.npcs.blocks.tiles.TileScriptedDoor;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import top.cnpcplus.script.tools.ScriptApiRegistry;

/** Read-only binding/event descriptions. Does not instantiate entities, call script APIs or inject bindings. */
public final class ScriptMetadataApi {
    public enum BindingKind { VALUE, TYPE, FUNCTION }
    /** Binary Java class names, including '$' for nested event classes. Empty type means unknown. */
    public record HookDescriptor(String name, String eventTypeName) {}
    public record GlobalDescriptor(String name, String typeName, BindingKind kind) {}
    private record Globals(Set<String> contexts, List<GlobalDescriptor> values) {}
    private static volatile Map<String, Globals> registrations = Map.of();
    private ScriptMetadataApi() {}
    /** Stable context derived from the actual handler type, never from a GUI class name. */
    public static String contextId(IScriptHandler handler) {
        if (handler instanceof DataScript) return "cnpcplus:npc";
        if (handler instanceof PlayerScriptData) return "cnpcplus:player";
        if (handler instanceof TileScriptedDoor) return "cnpcplus:block_door";
        if (handler instanceof TileScripted) return "cnpcplus:block";
        if (handler instanceof ItemScriptedWrapper) return "cnpcplus:item";
        if (handler instanceof ForgeScriptData) return "cnpcplus:forge_event";
        return "cnpcplus:unknown";
    }
    /** Bridge for existing completion contexts; new providers receive the stable context id instead. */
    public static String legacyContext(String contextId) {
        return switch (contextId) {
            case "cnpcplus:npc" -> "NpcEvent";
            case "cnpcplus:player" -> "PlayerEvent";
            case "cnpcplus:block" -> "BlockEvent";
            case "cnpcplus:block_door" -> "BlockDoorEvent";
            case "cnpcplus:item" -> "ItemEvent";
            case "cnpcplus:forge_event" -> "ForgeEvent";
            default -> contextId;
        };
    }
    public static List<HookDescriptor> hooks(String contextId) {
        EnumScriptType[] types = switch (contextId) {
            case "cnpcplus:npc" -> EnumScriptType.npcScripts;
            case "cnpcplus:player" -> EnumScriptType.playerScripts;
            case "cnpcplus:block" -> EnumScriptType.blockScripts;
            case "cnpcplus:block_door" -> EnumScriptType.doorScripts;
            case "cnpcplus:item" -> EnumScriptType.itemScripts;
            default -> new EnumScriptType[0];
        };
        if (contextId.equals("cnpcplus:forge_event")) return hooks(contextId, noppes.npcs.ForgeEventHandler.eventNames);
        return hooks(contextId, Arrays.stream(types).map(type -> type.function).toList());
    }
    /** Uses raw server-supplied names, retaining unknown third-party hooks without fabricating their type. */
    public static List<HookDescriptor> hooks(String contextId, List<String> names) {
        return names.stream().map(name -> {
            Class<?> type = ApiCompletion.hookEventType(legacyContext(contextId), name);
            return new HookDescriptor(name, type == null ? "" : type.getName());
        }).toList();
    }
    public static List<GlobalDescriptor> globals(String contextId) {
        var globals = new TreeMap<String, GlobalDescriptor>();
        ScriptContainer.Data.forEach((name, value) -> {
            if (value != null) globals.put(name, new GlobalDescriptor(name, value instanceof Class<?> type ? type.getName() : value.getClass().getName(),
                    value instanceof Class<?> ? BindingKind.TYPE : BindingKind.VALUE));
        });
        for (var entry : ScriptApiRegistry.roots().entrySet())
            if (!entry.getKey().equals("Math")) globals.putIfAbsent(entry.getKey(), new GlobalDescriptor(entry.getKey(), entry.getValue().getName(), BindingKind.VALUE));
        globals.put("dump", new GlobalDescriptor("dump", "noppes.npcs.controllers.ScriptContainer$Dump", BindingKind.VALUE));
        globals.put("log", new GlobalDescriptor("log", "noppes.npcs.controllers.ScriptContainer$Log", BindingKind.VALUE));
        for (var entry : registrations.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
            if (entry.getValue().contexts.contains(contextId)) for (var descriptor : entry.getValue().values) globals.put(descriptor.name(), descriptor);
        return List.copyOf(globals.values());
    }
    /** Same provider id replaces. Different providers cannot claim the same name in an overlapping context. */
    public static synchronized void registerGlobals(String providerId, Set<String> contextIds, List<GlobalDescriptor> descriptors) {
        if (providerId == null || !providerId.matches("[a-z0-9_.-]+:[a-z0-9_.-]+") || contextIds == null || contextIds.isEmpty())
            throw new IllegalArgumentException("providerId/contextIds");
        var contexts = Set.copyOf(contextIds); var values = List.copyOf(descriptors); var names = new HashSet<String>();
        for (var value : values) {
            if (value.name() == null || !value.name().matches("[a-zA-Z_$][a-zA-Z0-9_$]*") || value.typeName() == null || value.kind() == null || !names.add(value.name()))
                throw new IllegalArgumentException("global descriptor");
            if (ScriptContainer.Data.containsKey(value.name()) || ScriptApiRegistry.roots().containsKey(value.name()) || Set.of("dump", "log").contains(value.name()))
                throw new IllegalArgumentException("Global " + value.name() + " belongs to the host");
        }
        for (var entry : registrations.entrySet())
            if (!entry.getKey().equals(providerId) && !Collections.disjoint(contexts, entry.getValue().contexts)
                    && entry.getValue().values.stream().anyMatch(value -> names.contains(value.name())))
                throw new IllegalArgumentException("Global conflict with " + entry.getKey());
        var updated = new TreeMap<>(registrations); updated.put(providerId, new Globals(contexts, values)); registrations = Map.copyOf(updated);
    }
    public static synchronized void unregisterGlobals(String providerId) {
        var updated = new TreeMap<>(registrations); updated.remove(providerId); registrations = Map.copyOf(updated);
    }
}
