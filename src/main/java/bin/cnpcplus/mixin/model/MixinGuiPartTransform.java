package bin.cnpcplus.mixin.model;

import bin.cnpcplus.model.PartTransform;
import bin.cnpcplus.model.client.SubGuiPartTransform;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.ModelPartData;
import noppes.npcs.client.gui.model.GuiCreationParts;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.constants.EnumParts;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "noppes.npcs.client.gui.model.GuiCreationParts$GuiPart", remap = false)
public abstract class MixinGuiPartTransform {
    @Shadow @Final GuiCreationParts this$0;
    @Shadow protected ModelPartData data;
    @Shadow EnumParts part;

    @Inject(method = "initGui", at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$options(CallbackInfoReturnable<Integer> cir) {
        if (data == null || part == EnumParts.PARTICLES || part == EnumParts.LEGS && data.type <= 0) return;
        int y = cir.getReturnValue();
        this$0.addButton(new GuiNpcButton(94, this$0.guiLeft + 102, y, 143, 20, "cnpcplus.part.transform"));
        cir.setReturnValue(y + 25);
    }
    @Inject(method = "actionPerformed", at = @At("HEAD"), require = 1)
    private void cnpcplus$open(GuiButton button, CallbackInfo ci) {
        if (button.id == 94 && data != null) this$0.setSubGui(new SubGuiPartTransform(((PartTransform.Access)data).cnpcplus$partTransform()));
    }
}
