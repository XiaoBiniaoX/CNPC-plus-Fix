package bin.cnpcplus.mixin.clone;

import bin.cnpcplus.clone.CloneMounts;
import java.io.IOException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import noppes.npcs.*;
import noppes.npcs.constants.EnumPacketClient;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.controllers.data.PlayerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinCloneMountCapture {
    @Inject(method = "invoke(Lnet/minecraftforge/event/entity/player/PlayerInteractEvent$EntityInteract;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$copy(PlayerInteractEvent.EntityInteract event, CallbackInfo ci) {
        if (event.getHand() != EnumHand.MAIN_HAND || event.getTarget() instanceof EntityPlayer
                || event.getEntityPlayer().getHeldItemMainhand().getItem() != CustomItems.cloner) return;
        ci.cancel(); event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        // The client already sent its entity packet. Consume this action so it cannot fall through to the air menu.
        if (event.getWorld().isRemote) return;
        if (CustomNpcs.OpsOnly && !NoppesUtilServer.isOp(event.getEntityPlayer())
                || !CustomNpcsPermissions.hasPermission(event.getEntityPlayer(), CustomNpcsPermissions.NPC_CLONE)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        try {
            NBTTagCompound tag = CloneMounts.capture(event.getTarget()); ServerCloneController.Instance.cleanTags(tag);
            if (Server.sendDataChecked(player, EnumPacketClient.CLONE, tag)) PlayerData.get(player).cloned = tag;
            else player.sendMessage(new TextComponentString("骑乘组合过大，保留原克隆"));
        } catch (IOException | RuntimeException error) {
            player.sendMessage(new TextComponentString("未复制：" + error.getMessage()));
        }
    }
}
