package bin.cnpcplus.recipe;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /cnpcplus recipedebug — world self-check for Phase3 funnel.
 */
public final class RecipeDebugCommand {
    private RecipeDebugCommand() {}

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("cnpcplus")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("recipedebug")
                        .executes(ctx -> {
                            RecipeDebug.ENABLED = true;
                            RecipeDebug.probeAllGlobals();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "[cnpcplus] RecipeDebug probeAllGlobals written to log"), true);
                            return 1;
                        })));
    }
}