package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.StyledText;
import bin.cnpcplus.text.TextSpans;
import bin.cnpcplus.text.client.StyledChatMessages;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import noppes.npcs.NoppesStringUtils;
import noppes.npcs.Server;
import noppes.npcs.client.PacketHandlerClient;
import noppes.npcs.client.RenderChatMessages;
import noppes.npcs.constants.EnumPacketClient;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketHandlerClient.class, remap = false)
public abstract class MixinChatBubblePacketStyles {
    @Inject(method = "client(Lio/netty/buffer/ByteBuf;Lnet/minecraft/entity/player/EntityPlayer;Lnoppes/npcs/constants/EnumPacketClient;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$chat(ByteBuf buffer, EntityPlayer player, EnumPacketClient type, CallbackInfo ci) {
        if (type != EnumPacketClient.CHATBUBBLE) return;
        ci.cancel();
        Entity entity = Minecraft.getMinecraft().world.getEntityByID(buffer.readInt());
        if (!(entity instanceof EntityNPCInterface)) return;
        EntityNPCInterface npc = (EntityNPCInterface) entity;
        String raw = Server.readString(buffer);
        boolean show = buffer.readBoolean();
        String spans = buffer.isReadable() ? Server.readString(buffer) : "";
        StyledText text = new StyledText(raw, spans).format(value -> NoppesStringUtils.formatText(value, player, npc)).legacy();
        if (npc.messages == null) npc.messages = new RenderChatMessages();
        if (npc.messages instanceof StyledChatMessages) ((StyledChatMessages) npc.messages).cnpcplus$addStyled(text, npc);
        else npc.messages.addMessage(text.text, npc);
        if (!show) return;
        // 1.12 chat only supports its native sixteen colors; bubble/editor keep exact RGB.
        TextComponentString chat = new TextComponentString(npc.getName() + ": ");
        int[][] styles = TextSpans.read(text.spans, text.text.length());
        for (int start = 0; start < text.text.length();) {
            int end = start + 1;
            while (end < text.text.length() && styles[0][end] == styles[0][start] && styles[1][end] == styles[1][start]) end++;
            Style style = new Style().setBold((styles[1][start] & 1) != 0).setUnderlined((styles[1][start] & 2) != 0).setItalic((styles[1][start] & 4) != 0);
            if (styles[0][start] >= 0) {
                int distance = Integer.MAX_VALUE; TextFormatting nearest = TextFormatting.WHITE;
                for (TextFormatting color : TextFormatting.values()) if (color.isColor()) {
                    int rgb = Minecraft.getMinecraft().fontRenderer.getColorCode(color.toString().charAt(1));
                    int dr = (rgb >> 16 & 255) - (styles[0][start] >> 16 & 255), dg = (rgb >> 8 & 255) - (styles[0][start] >> 8 & 255), db = (rgb & 255) - (styles[0][start] & 255);
                    int delta = dr * dr + dg * dg + db * db;
                    if (delta < distance) { distance = delta; nearest = color; }
                }
                style.setColor(nearest);
            }
            chat.appendSibling(new TextComponentString(text.text.substring(start, end)).setStyle(style)); start = end;
        }
        player.sendMessage(chat);
    }
}
