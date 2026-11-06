package bin.cnpcplus.mixin.shader;

import java.io.File;
import net.minecraft.client.resources.AbstractResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractResourcePack.class)
public interface ResourcePackAccess {
    @Accessor("resourcePackFile") File cnpcplus$file();
}
