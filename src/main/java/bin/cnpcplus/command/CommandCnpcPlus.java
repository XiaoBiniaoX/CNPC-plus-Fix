package bin.cnpcplus.command;

import java.io.File;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.SyncController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.util.NBTJsonUtil;

/**
 * /cnpcplus playerdata load <player>
 * 权限 4。从磁盘 uuid.json 重新读取该玩家的 CNPC 数据（对话/任务/传送点/阵营/邮件）
 * 并覆盖内存中的 capability，随后 save(true) 同步客户端。
 * 玩家必须在线——离线数据本就在磁盘上，无需「读档」。
 */
public class CommandCnpcPlus extends CommandBase {

    @Override
    public String getName() {
        return "cnpcplus";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/cnpcplus playerdata load <player>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public List<String> getAliases() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2 || !"playerdata".equalsIgnoreCase(args[0]) || !"load".equalsIgnoreCase(args[1])) {
            throw new CommandException(getUsage(sender), new Object[0]);
        }
        if (args.length < 3) {
            throw new CommandException("commands.generic.syntax", new Object[0]);
        }
        EntityPlayerMP target = getPlayer(server, sender, args[2]);
        File saveDir = CustomNpcs.getWorldSaveDirectory("playerdata");
        File file = new File(saveDir, target.getUniqueID().toString() + ".json");
        if (!file.exists()) {
            sender.sendMessage(new TextComponentString("[CNPCPlus] No data file for " + target.getName()));
            return;
        }
        try {
            net.minecraft.nbt.NBTTagCompound nbt = NBTJsonUtil.LoadFile(file);
            PlayerData data = PlayerData.get(target);
            data.setNBT(nbt);
            data.save(true);
            SyncController.syncPlayer(target);
            sender.sendMessage(new TextComponentString("[CNPCPlus] Reloaded player data for " + target.getName()));
        } catch (Exception e) {
            throw new CommandException("Failed to load player data: " + e.getMessage(), new Object[0]);
        }
    }
}
