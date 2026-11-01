package top.cnpcplus.mixin;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.gui.roles.GuiNpcFollowerSetup;
import noppes.npcs.roles.RoleFollower;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.follower.FollowerMessages;

@Mixin(value = GuiNpcFollowerSetup.class, remap = false)
public abstract class MixinGuiNpcFollowerMessages {
    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$fourMessageFields(CallbackInfo ci) {
        GuiNpcFollowerSetup self = (GuiNpcFollowerSetup) (Object) this;
        self.getTextField(3).setWidth(138);
        self.getTextField(4).setWidth(138);
        FollowerMessages.Settings messages = FollowerMessages.get((RoleFollower) self.npc.role);
        String[] names = {"limit", "dismiss"}, values = {messages.limit(), messages.dismiss()};
        for (int i = 0; i < 2; i++) {
            String key = "cnpcplus.follower." + names[i];
            int y = self.guiTop + 6 + 24 * i;
            GuiTextFieldNop field = new GuiTextFieldNop(420 + i, self, self.guiLeft + 248, y, 138, 20, values[i]);
            field.setMaxLength(32767);
            field.setTooltip(Tooltip.create(Component.translatable(key + ".tooltip")));
            self.addTextField(field);
            self.addLabel(new GuiLabel(420 + i, key, self.guiLeft + 389, y + 5));
        }
    }

    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void cnpcplus$saveMessages(CallbackInfo ci) {
        GuiNpcFollowerSetup self = (GuiNpcFollowerSetup) (Object) this;
        FollowerMessages.put((RoleFollower) self.npc.role,
                new FollowerMessages.Settings(self.getTextField(420).getValue(), self.getTextField(421).getValue()));
    }
}
