package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.ArrayLine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.Server;
import noppes.npcs.constants.EnumPacketClient;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcLineStyles {
    @Redirect(method = "say(Lnet/minecraft/entity/player/EntityPlayer;Lnoppes/npcs/controllers/data/Line;)V",
            at = @At(value = "INVOKE", target = "Lnoppes/npcs/Server;sendData(Lnet/minecraft/entity/player/EntityPlayerMP;Lnoppes/npcs/constants/EnumPacketClient;[Ljava/lang/Object;)V"), require = 2)
    private void cnpcplus$styles(EntityPlayerMP receiver, EnumPacketClient type, Object[] values, EntityPlayer player, Line line) {
        if (type == EnumPacketClient.CHATBUBBLE && line instanceof ArrayLine) {
            Object[] styled = java.util.Arrays.copyOf(values, values.length + 1);
            styled[values.length] = ((ArrayLine) line).spans;
            Server.sendData(receiver, type, styled);
        } else Server.sendData(receiver, type, values);
    }
}
