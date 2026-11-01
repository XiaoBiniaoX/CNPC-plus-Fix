package top.cnpcplus;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import top.cnpcplus.client.NpcAlphaVertexConsumer;

public final class NpcAlphaRegression {
    public static void main(String[] args) {
        int[] color = new int[4];
        VertexConsumer sink = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (proxy, method, values) -> {
                    if (method.getName().equals("color") || method.getName().equals("defaultColor")) {
                        for (int i = 0; i < 4; i++) color[i] = (int) values[i];
                    }
                    return method.getReturnType() == VertexConsumer.class ? proxy : null;
                });
        VertexConsumer half = new NpcAlphaVertexConsumer(sink);
        check(half.color(10, 20, 30, 254) == half, "fluent chain escaped multiplier");
        check(Arrays.equals(color, new int[]{10, 20, 30, 127}), "RGB changed or alpha not halved");
        half.defaultColor(40, 50, 60, 100);
        check(Arrays.equals(color, new int[]{40, 50, 60, 50}), "default alpha ignored");
        half.color(1f, 0.5f, 0f, 1f);
        check(Arrays.equals(color, new int[]{255, 127, 0, 127}), "float color bypassed multiplier");
        half.vertex(1f, 2f, 3f, 1f, 0f, 0f, 0.5f, 0f, 0f, 0, 0, 0f, 1f, 0f);
        check(Arrays.equals(color, new int[]{255, 0, 0, 63}), "bulk/model vertex bypassed multiplier");
        System.out.println("NPC_ALPHA_REGRESSION=PASS");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
