package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.villagertrade.PacketVillageAction;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.mainmenu.GuiNpcAdvanced;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;
import java.util.Arrays;

@Mixin(value = GuiNpcAdvanced.class, remap = false)
public abstract class MixinGuiNpcAdvancedVillager {
    @Inject(method = "func_73866_w_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$extendRoleList(CallbackInfo ci) {
        GuiNpcAdvanced self = (GuiNpcAdvanced) (Object) this;
        GuiNpcButton roles = self.getButton(8);
        try {
            Field field = GuiNpcButton.class.getDeclaredField("display"); field.setAccessible(true);
            String[] old = (String[]) field.get(roles);
            String[] names = Arrays.copyOf(old, 9); names[8] = "role.villagertrader";
            field.set(roles, names); roles.setDisplay(self.npc.advanced.role);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot extend NPC roles", error); }
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
