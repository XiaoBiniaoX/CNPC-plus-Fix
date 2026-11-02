package bin.cnpcplus.mixin.script;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EditBox.class, remap = false)
public interface EditBoxScrollAccess {
    @Accessor("displayPos") int cnpcplus$displayPos();
}
