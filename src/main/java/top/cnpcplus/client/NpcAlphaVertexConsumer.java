package top.cnpcplus.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;

/** Part vertices retain their own tint/alpha; only multiply the NPC's partial visibility. */
public final class NpcAlphaVertexConsumer extends VertexConsumerWrapper {
    public NpcAlphaVertexConsumer(VertexConsumer parent) {
        super(parent);
    }

    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        parent.color(r, g, b, a / 2);
        return this;
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
        parent.defaultColor(r, g, b, a / 2);
    }
}
