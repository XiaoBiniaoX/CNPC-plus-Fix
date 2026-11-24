package bin.cnpcplus.model.client;

import bin.cnpcplus.model.PartTransform;
import net.minecraft.client.renderer.GlStateManager;
import noppes.npcs.ModelPartData;

public final class PartTransforms {
    private PartTransforms() { }
    public static void apply(ModelPartData data) {
        PartTransform t = ((PartTransform.Access)data).cnpcplus$partTransform();
        if (t.isIdentity()) return;
        GlStateManager.translate(t.get(3) / 16F, t.get(4) / 16F, t.get(5) / 16F);
        GlStateManager.scale(t.get(0), t.get(1), t.get(2));
    }
}
