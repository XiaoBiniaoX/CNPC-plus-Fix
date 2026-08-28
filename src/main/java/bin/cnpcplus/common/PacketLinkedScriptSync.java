package bin.cnpcplus.common;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.CustomNpcsPermissions;
import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.controllers.LinkedNpcController.LinkedData;
import bin.cnpcplus.common.ILinkedDataScriptSyncAccess;

import java.util.HashMap;
import java.util.Map;

public class PacketLinkedScriptSync implements IMessage {

    private static final int MAX_NAME_LENGTH = 128;
    private static final int MAX_STATES = 1024;

    private static volatile Map<String, Boolean> pendingStates = null;

    public static Map<String, Boolean> takePendingStates() {
        Map<String, Boolean> p = pendingStates;
        pendingStates = null;
        return p;
    }

    public byte action;
    public String name;
    public HashMap<String, Boolean> states;
    private boolean valid = true;

    public PacketLinkedScriptSync() {}

    public static PacketLinkedScriptSync createRequest() {
        PacketLinkedScriptSync p = new PacketLinkedScriptSync();
        p.action = 0;
        return p;
    }

    public static PacketLinkedScriptSync createToggle(String name) {
        PacketLinkedScriptSync p = new PacketLinkedScriptSync();
        p.action = 1;
        p.name = name == null ? "" : name;
        return p;
    }

    public static PacketLinkedScriptSync createResponse(HashMap<String, Boolean> states) {
        PacketLinkedScriptSync p = new PacketLinkedScriptSync();
        p.action = 2;
        p.states = states;
        return p;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            if (!buf.isReadable()) {
                valid = false;
                return;
            }
            action = buf.readByte();
            if (action == 1) {
                name = ByteBufUtils.readUTF8String(buf);
                valid = name != null && !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
            } else if (action == 2) {
                if (buf.readableBytes() < 4) {
                    valid = false;
                    return;
                }
                int size = buf.readInt();
                if (size < 0 || size > MAX_STATES) {
                    valid = false;
                    return;
                }
                states = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    String key = ByteBufUtils.readUTF8String(buf);
                    if (key == null || key.isEmpty() || key.length() > MAX_NAME_LENGTH || !buf.isReadable()) {
                        valid = false;
                        states.clear();
                        return;
                    }
                    states.put(key, buf.readBoolean());
                }
            } else if (action != 0) {
                valid = false;
            }
        } catch (RuntimeException malformed) {
            valid = false;
            states = null;
            name = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(action);
        if (action == 1) {
            ByteBufUtils.writeUTF8String(buf, name == null ? "" : name);
        } else if (action == 2) {
            Map<String, Boolean> safeStates = states == null ? java.util.Collections.emptyMap() : states;
            buf.writeInt(Math.min(safeStates.size(), MAX_STATES));
            int written = 0;
            for (Map.Entry<String, Boolean> e : safeStates.entrySet()) {
                if (written++ >= MAX_STATES) break;
                ByteBufUtils.writeUTF8String(buf, e.getKey());
                buf.writeBoolean(e.getValue());
            }
        }
    }

    public static class Handler implements IMessageHandler<PacketLinkedScriptSync, PacketLinkedScriptSync> {
        @Override
        public PacketLinkedScriptSync onMessage(PacketLinkedScriptSync message, MessageContext ctx) {
            if (ctx.side == Side.SERVER && message.valid) {
                net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
                player.getServerWorld().addScheduledTask(() -> {
                    if (message.action == 0) {
                        HashMap<String, Boolean> states = new HashMap<>();
                        for (LinkedData data : LinkedNpcController.Instance.list) {
                            if (data instanceof ILinkedDataScriptSyncAccess && data.name != null) {
                                ILinkedDataScriptSyncAccess ext = (ILinkedDataScriptSyncAccess) data;
                                states.put(data.name, ext.cnpcplus$getScriptSync());
                            }
                        }
                        bin.cnpcplus.craftingview.network.CraftingViewNetwork.CHANNEL.sendTo(
                                PacketLinkedScriptSync.createResponse(states), player);
                    } else if (message.action == 1
                            && CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.GLOBAL_LINKED)) {
                        LinkedData data = LinkedNpcController.Instance.getData(message.name);
                        if (data instanceof ILinkedDataScriptSyncAccess) {
                            ILinkedDataScriptSyncAccess ext = (ILinkedDataScriptSyncAccess) data;
                            ext.cnpcplus$setScriptSync(!ext.cnpcplus$getScriptSync());
                            LinkedNpcController.Instance.save();
                            HashMap<String, Boolean> states = new HashMap<>();
                            for (LinkedData d : LinkedNpcController.Instance.list) {
                                if (d instanceof ILinkedDataScriptSyncAccess && d.name != null) {
                                    ILinkedDataScriptSyncAccess ext2 = (ILinkedDataScriptSyncAccess) d;
                                    states.put(d.name, ext2.cnpcplus$getScriptSync());
                                }
                            }
                            bin.cnpcplus.craftingview.network.CraftingViewNetwork.CHANNEL.sendTo(
                                    PacketLinkedScriptSync.createResponse(states), player);
                        }
                    }
                });
            }
            return null;
        }
    }

    public static class ClientHandler implements IMessageHandler<PacketLinkedScriptSync, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketLinkedScriptSync message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT && message.valid && message.action == 2 && message.states != null) {
                PacketLinkedScriptSync.pendingStates = message.states;
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                    net.minecraft.client.gui.GuiScreen screen = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
                    if (screen instanceof ILinkedScriptSyncGui) {
                        ((ILinkedScriptSyncGui) screen).cnpcplus$acceptScriptSyncStates(message.states);
                    }
                });
            }
            return null;
        }
    }
}
