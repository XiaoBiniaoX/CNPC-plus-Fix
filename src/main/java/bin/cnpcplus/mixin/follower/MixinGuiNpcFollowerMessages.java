package bin.cnpcplus.mixin.follower;

import bin.cnpcplus.follower.FollowerMessages;
import net.minecraft.inventory.Container;
import noppes.npcs.client.gui.roles.GuiNpcFollowerSetup;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface2;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleFollower;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcFollowerSetup.class, remap = false)
public abstract class MixinGuiNpcFollowerMessages extends GuiContainerNPCInterface2 {
    @Shadow private RoleFollower role;
    protected MixinGuiNpcFollowerMessages(EntityNPCInterface npc, Container container) { super(npc, container); }
    @Inject(method = "func_73866_w_", at = @At("TAIL"), require = 1)
    private void cnpcplus$fields(CallbackInfo ci) {
        FollowerMessages.Settings messages = FollowerMessages.get(role);
        addTextField(new GuiNpcTextField(3, this, fontRenderer, field_147003_i + 100, field_147009_r + 6, 140, 20, role.dialogHire));
        addTextField(new GuiNpcTextField(4, this, fontRenderer, field_147003_i + 100, field_147009_r + 30, 140, 20, role.dialogFarewell));
        addTextField(new GuiNpcTextField(410, this, fontRenderer, field_147003_i + 246, field_147009_r + 6, 140, 20, messages.limit));
        addTextField(new GuiNpcTextField(411, this, fontRenderer, field_147003_i + 246, field_147009_r + 30, 140, 20, messages.dismiss));
        addLabel(new GuiNpcLabel(410, "cnpcplus.follower.limitText", field_147003_i + 246, field_147009_r - 4));
        addLabel(new GuiNpcLabel(411, "cnpcplus.follower.dismissText", field_147003_i + 246, field_147009_r + 53));
    }
    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void cnpcplus$save(CallbackInfo ci) {
        FollowerMessages.put(role, new FollowerMessages.Settings(getTextField(410).getText(), getTextField(411).getText()));
    }
}
