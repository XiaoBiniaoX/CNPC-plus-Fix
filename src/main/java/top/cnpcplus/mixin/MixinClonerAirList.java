package top.cnpcplus.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import noppes.npcs.constants.EnumGuiType;
import noppes.npcs.items.ItemNpcCloner;
import noppes.npcs.packets.server.SPacketGuiOpen;
import org.spongepowered.asm.mixin.Mixin;

/** Air opens the shared copy page; useOn keeps the native spawn page. */
@Mixin(value = ItemNpcCloner.class, remap = false)
public abstract class MixinClonerAirList {
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) SPacketGuiOpen.sendOpenGui(player, EnumGuiType.NpcRemote, null, player.blockPosition());
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
