package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.villagertrade.PacketVillageAction;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

@Mixin(value = GuiNpcAdvanced.class, remap = false)
public abstract class MixinGuiNpcAdvancedVillager {
    private static final String BI_DIR_CTOR =
            "Lnoppes/npcs/client/gui/util/GuiButtonBiDirectional;<init>(IIIII[Ljava/lang/String;I)V";

    @ModifyArg(method = "func_73866_w_", at = @At(value = "INVOKE", target = BI_DIR_CTOR, ordinal = 0), index = 5)
    private String[] cnpcplus$extendRoleArray(String[] old) {
        String[] names = Arrays.copyOf(old, old.length + 1);
        names[old.length] = "role.villagertrader";
        return names;
    }

    @Inject(method = "setGuiData", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openVillagerEditor(NBTTagCompound tag, CallbackInfo ci) {
        GuiNpcAdvanced self = (GuiNpcAdvanced) (Object) this;
        if (!tag.hasKey("RoleData") || !tag.hasKey("TradeListId") || self.npc.advanced.role != 8) return;
        self.npc.roleInterface.readFromNBT(tag);
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketVillageAction(PacketVillageAction.OPEN_EDIT, -1,
                self.npc.getEntityId(), new NBTTagCompound()));
        ci.cancel();
    }
}
