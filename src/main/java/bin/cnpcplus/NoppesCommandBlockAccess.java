package bin.cnpcplus;

import com.mojang.brigadier.tree.CommandNode;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /noppes and its subcommands are gated behind hasPermission(2) or hasPermission(4).
 * A command block runs at permission level 2 with no entity attached, so every level-4
 * subcommand (clone add/remove, config, schema, slay, dialog reload, quest reload...) is
 * silently unavailable there.
 *
 * Every node under /noppes gets its requirement widened so that a non-player level-2 source
 * (command block, function, /execute chain from one) passes. Player-facing permissions are
 * left exactly as CustomNPCs declared them.
 *
 * Brigadier lives on the boot layer, so a Mixin accessor cannot be applied to CommandNode.
 * The field is final with no setter, hence reflection.
 */
public final class NoppesCommandBlockAccess {
    private NoppesCommandBlockAccess() {}

    private static Field requirementField;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandNode<CommandSourceStack> root = event.getDispatcher().getRoot().getChild("noppes");
        if (root == null) {
            return;
        }
        try {
            if (requirementField == null) {
                requirementField = CommandNode.class.getDeclaredField("requirement");
                requirementField.setAccessible(true);
            }
            widen(root, Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            CnpcPlus.LOGGER.error("Failed to widen /noppes for command blocks", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void widen(CommandNode<CommandSourceStack> node, Set<CommandNode<CommandSourceStack>> seen)
            throws ReflectiveOperationException {
        if (!seen.add(node)) {
            return;
        }
        Predicate<CommandSourceStack> original = node.getRequirement();
        requirementField.set(node, (Predicate<CommandSourceStack>)
                s -> original.test(s) || (!(s.getEntity() instanceof ServerPlayer) && s.hasPermission(2)));
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            widen(child, seen);
        }
    }
}
