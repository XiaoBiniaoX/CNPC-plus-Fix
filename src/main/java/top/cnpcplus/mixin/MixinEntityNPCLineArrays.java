package top.cnpcplus.mixin;

import noppes.npcs.controllers.data.Line;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import noppes.npcs.packets.client.PacketChatBubble;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.lines.ArrayLine;

@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCLineArrays {
    @ModifyVariable(method = {"say", "saySurrounding"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Line cnpcplus$playbackCopy(Line line) {
        return line instanceof ArrayLine array ? array.forPlayback() : line;
    }

    @Redirect(method = "say", at = @At(value = "NEW", target = "(ILnet/minecraft/network/chat/Component;Z)Lnoppes/npcs/packets/client/PacketChatBubble;"))
    private PacketChatBubble cnpcplus$styledChat(int id, Component text, boolean shown, net.minecraft.world.entity.player.Player player, Line line) {
        return new PacketChatBubble(id, TextStyles.get(line, TextStyles.LINE, line.getText()).legacy().component(), shown);
    }
}
