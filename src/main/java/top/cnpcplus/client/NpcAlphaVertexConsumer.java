package top.cnpcplus.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.neoforge.client.model.pipeline.VertexConsumerWrapper;

/** Preserve each part's tint and existing alpha, multiplying only partial NPC visibility. */
public final class NpcAlphaVertexConsumer extends VertexConsumerWrapper {
    public NpcAlphaVertexConsumer(VertexConsumer parent) { super(parent); }

    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        parent.setColor(r, g, b, a / 2);
        return this;
    }
}
