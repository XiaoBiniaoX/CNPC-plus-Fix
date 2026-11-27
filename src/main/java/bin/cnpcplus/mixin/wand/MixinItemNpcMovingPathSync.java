package bin.cnpcplus.mixin.wand;

import bin.cnpcplus.wand.PacketWandPathSync;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.items.ItemNpcMovingPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemNpcMovingPath.class, remap = false)
public abstract class MixinItemNpcMovingPathSync {
    @Inject(method = "func_180614_a", at = @At("RETURN"), require = 1)
    private void cnpcplus$syncAddedPoint(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
            EnumFacing side, float hitX, float hitY, float hitZ, CallbackInfoReturnable<EnumActionResult> cir) {
        if (world.isRemote || cir.getReturnValue() != EnumActionResult.SUCCESS) return;
        ItemStack held = player.getHeldItem(hand);
        if (!held.hasTagCompound()) return;
        Entity target = world.getEntityByID(held.getTagCompound().getInteger("NPCID"));
        if (target instanceof EntityNPCInterface && !target.isDead) PacketWandPathSync.send((EntityNPCInterface) target);
    }
}
