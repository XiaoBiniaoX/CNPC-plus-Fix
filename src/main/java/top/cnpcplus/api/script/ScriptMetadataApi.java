package top.cnpcplus.api.script;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import top.cnpcplus.script.ScriptApiCompletion;
import top.cnpcplus.script.tools.ScriptApiRegistry;

/** Stable, read-only metadata for the explicitly registered CNPCplus script surface. */
public final class ScriptMetadataApi {
    public record GlobalDescriptor(String name, String typeName, String sourceMod, String side,
                                   String threadPolicy, String lifecycle, String kind, String description) {}

    public record MemberDescriptor(String ownerType, String name, String signature, String targetType,
                                   String kind, boolean staticMember, boolean readable, boolean writable,
                                   boolean inherited, String deprecatedSince, boolean parameterNamesTrusted,
                                   String behaviorNotes) {}

    public record ApiCatalog(int schemaVersion, int apiLevel, String buildId, String minecraftVersion,
                             String loader, List<GlobalDescriptor> globals,
                             Map<String, List<ScriptPageApi.HookDescriptor>> hooks) {
        public ApiCatalog {
            globals = List.copyOf(globals);
            hooks = hooks.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                    entry -> List.copyOf(entry.getValue())));
        }
    }

    private ScriptMetadataApi() {}

    public static ApiCatalog catalog() {
        var hooks = new LinkedHashMap<String, List<ScriptPageApi.HookDescriptor>>();
        for (ScriptPageApi.Carrier carrier : ScriptPageApi.Carrier.values()) {
            if (carrier != ScriptPageApi.Carrier.UNKNOWN) hooks.put(carrier.name(), hooks(carrier));
        }
        return new ApiCatalog(1, CnpcPlusApi.apiLevel(), CnpcPlusApi.buildId(), "1.20.1", "Forge",
                globals(), hooks);
    }

    public static List<GlobalDescriptor> globals() {
        var result = new ArrayList<GlobalDescriptor>();
        for (var entry : ScriptApiRegistry.roots().entrySet()) {
            String name = entry.getKey().equals("Math") ? "CNPCPlusMath" : entry.getKey();
            result.add(new GlobalDescriptor(name, entry.getValue().getName(), "cnpcplus", "BOTH",
                    "ANY_THREAD", "VALUE", "TOOL", "Registered CNPCplus script module"));
        }
        result.sort(Comparator.comparing(GlobalDescriptor::name));
        return List.copyOf(result);
    }

    public static List<ScriptPageApi.HookDescriptor> hooks(ScriptPageApi.Carrier carrier) {
        if (carrier == null || carrier == ScriptPageApi.Carrier.UNKNOWN) return List.of();
        noppes.npcs.constants.EnumScriptType[] values = switch (carrier) {
            case NPC -> noppes.npcs.constants.EnumScriptType.npcScripts;
            case PLAYER -> noppes.npcs.constants.EnumScriptType.playerScripts;
            case BLOCK -> noppes.npcs.constants.EnumScriptType.blockScripts;
            case BLOCK_DOOR -> noppes.npcs.constants.EnumScriptType.doorScripts;
            case ITEM -> noppes.npcs.constants.EnumScriptType.itemScripts;
            case FORGE -> new noppes.npcs.constants.EnumScriptType[0];
            case UNKNOWN -> new noppes.npcs.constants.EnumScriptType[0];
        };
        var names = new ArrayList<String>();
        for (var value : values) if (value != null) names.add(value.function);
        return ScriptPageApi.hooks(names, carrier);
    }

    public static List<MemberDescriptor> members(String ownerType) {
        if (ownerType == null || ownerType.isBlank()) return List.of();
        Class<?> owner;
        try {
            owner = Class.forName(ownerType, false, ScriptMetadataApi.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError unavailable) {
            return List.of();
        }
        var result = new ArrayList<MemberDescriptor>();
        for (var entry : ScriptApiCompletion.members(owner)) {
            String kind = entry.method ? "METHOD" : "PROPERTY_ALIAS";
            result.add(new MemberDescriptor(owner.getName(), entry.name, entry.signature,
                    entry.type == null ? "?" : entry.type.getTypeName(), kind, false, true, false,
                    false, "", false, "CNPC public script metadata"));
        }
        result.sort(Comparator.comparing(MemberDescriptor::name).thenComparing(MemberDescriptor::signature));
        return List.copyOf(result);
    }

    /** Stable JSON without world objects, class instances, absolute paths or user source. */
    public static String exportJson() {
        ApiCatalog data = catalog();
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "schemaVersion", Integer.toString(data.schemaVersion()), false);
        field(json, "apiLevel", Integer.toString(data.apiLevel()), false);
        field(json, "buildId", quote(data.buildId()), false);
        field(json, "minecraftVersion", quote(data.minecraftVersion()), false);
        field(json, "loader", quote(data.loader()), false);
        json.append("\"globals\":[");
        for (int i = 0; i < data.globals().size(); i++) {
            if (i > 0) json.append(',');
            GlobalDescriptor g = data.globals().get(i);
            json.append('{');
            field(json, "name", quote(g.name()), false); field(json, "typeName", quote(g.typeName()), false);
            field(json, "sourceMod", quote(g.sourceMod()), false); field(json, "side", quote(g.side()), false);
            field(json, "threadPolicy", quote(g.threadPolicy()), false); field(json, "lifecycle", quote(g.lifecycle()), false);
            field(json, "kind", quote(g.kind()), false); field(json, "description", quote(g.description()), true);
            json.append('}');
        }
        json.append("],\"hooks\":{");
        int carrierIndex = 0;
        for (var entry : data.hooks().entrySet()) {
            if (carrierIndex++ > 0) json.append(',');
            json.append(quote(entry.getKey())).append(':').append('[');
            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) json.append(',');
                var hook = entry.getValue().get(i);
                json.append('{');
                field(json, "name", quote(hook.name()), false); field(json, "carrier", quote(hook.carrier().name()), false);
                field(json, "sourceTypeName", quote(hook.sourceTypeName().orElse("")), false);
                field(json, "binaryTypeName", quote(hook.binaryTypeName().orElse("")), true);
                json.append('}');
            }
            json.append(']');
        }
        json.append("}}" );
        return json.toString();
    }

    private static void field(StringBuilder json, String name, String value, boolean last) {
        json.append(quote(name)).append(':').append(value);
        if (!last) json.append(',');
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(c < 0x20 ? String.format("\\u%04x", (int)c) : c);
            }
        }
        return result.append('"').toString();
    }
}
