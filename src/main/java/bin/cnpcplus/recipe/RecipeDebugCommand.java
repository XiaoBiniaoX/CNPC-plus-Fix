package bin.cnpcplus.recipe;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class RecipeDebugCommand extends CommandBase {
    @Override
    public String getName() {
        return "cnpcplus";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/cnpcplus recipedebug";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 1 && "recipedebug".equalsIgnoreCase(args[0])) {
            RecipeDebug.ENABLED = true;
            RecipeDebug.probeAllGlobals();
            sender.sendMessage(new TextComponentString("[cnpcplus] RecipeDebug probeAllGlobals written to log"));
            return;
        }
        sender.sendMessage(new TextComponentString(getUsage(sender)));
    }
}
