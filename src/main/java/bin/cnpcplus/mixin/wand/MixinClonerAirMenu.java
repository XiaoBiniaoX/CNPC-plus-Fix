package bin.cnpcplus.mixin.wand;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import noppes.npcs.CustomNpcs;
import noppes.npcs.constants.EnumGuiType;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.items.ItemNpcCloner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemNpcCloner.class, remap = false)
public abstract class MixinClonerAirMenu extends Item {
    @Override public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (world.isRemote) CustomNpcs.proxy.openGui(0, 0, 0, EnumGuiType.NpcRemote, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
    @Inject(method = "isAllowed", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$remote(EnumPacketServer type, CallbackInfoReturnable<Boolean> cir) {
        if (type == EnumPacketServer.RemoteNpcsGet || type == EnumPacketServer.RemoteDelete || type == EnumPacketServer.RemoteTpToNpc)
            cir.setReturnValue(true);
    }
}
