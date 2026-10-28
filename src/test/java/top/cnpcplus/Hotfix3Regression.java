package top.cnpcplus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.api.CustomNPCsException;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.NpcSightGuard;
import top.cnpcplus.mixin.MixinEntityWrapperFiniteInputs;
import top.cnpcplus.mixin.MixinWrapperFinitePosition;
import top.cnpcplus.mixin.MixinWrapperFiniteRotation;

/** Offline regression using the actual Minecraft traversal, with a bounded callback. */
public final class Hotfix3Regression {
    public static void main(String[] args) throws Exception {
        Vec3 from = new Vec3(0.5, 64.5, 0.5);
        Vec3 invalid = new Vec3(8.5, 64.5, Double.NaN);
        AtomicInteger visits = new AtomicInteger();
        boolean runaway = false;
        try {
            BlockGetter.traverseBlocks(from, invalid, visits, (count, pos) -> {
                if (count.incrementAndGet() > 4096) throw new IllegalStateException("runaway ray");
                return null;
            }, count -> true);
        } catch (IllegalStateException expected) {
            check("runaway ray".equals(expected.getMessage()), "unexpected traversal failure");
            runaway = true;
        }
        check(runaway, "vanilla NaN regression no longer reproduced");
        System.out.println("PASS: vanilla NaN ray exceeded 4096 cell visits (bounded reproduction)");

        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            for (Vec3 point : new Vec3[]{new Vec3(bad, 64, 0), new Vec3(0, bad, 0), new Vec3(0, 64, bad)}) {
                check(!NpcSightGuard.canTrace(from, point, pos -> { throw new AssertionError("invalid ray read chunks"); }), "invalid target accepted");
                check(!NpcSightGuard.canTrace(point, from, pos -> { throw new AssertionError("invalid ray read chunks"); }), "invalid source accepted");
            }
        }
        check(NpcSightGuard.canTrace(from, new Vec3(8.5, 66.5, 8.5), pos -> true), "normal ray rejected");
        check(NpcSightGuard.canTrace(from, from, pos -> true), "coincident eyes rejected");
        check(NpcSightGuard.canTrace(from, from.add(128, 0, 0), pos -> true), "128 block boundary rejected");
        check(!NpcSightGuard.canTrace(from, from.add(128.01, 0, 0), pos -> { throw new AssertionError("distant ray read chunks"); }), "distant ray accepted");
        // Only the traversed cells matter, not every chunk in the enclosing rectangle.
        check(NpcSightGuard.canTrace(from, new Vec3(40.5, 64.5, 40.5), pos -> !(pos.getX() < 16 && pos.getZ() >= 32)), "off-ray unloaded chunk blocked sight");
        visits.set(0);
        check(!NpcSightGuard.canTrace(from, new Vec3(40.5, 64.5, 0.5), pos -> {
            visits.incrementAndGet();
            return pos.getX() < 16;
        }), "unloaded chunk accepted");
        check(visits.get() <= 18, "did not stop at first unloaded cell");
        check(!NpcSightGuard.canTrace(new Vec3(0, 64, 0), new Vec3(16, 64, 0), pos -> pos.getX() < 16), "endpoint epsilon ignored");
        System.out.println("PASS: finite/NaN/infinity, distance, unloaded cells, off-ray chunks and endpoint boundaries");

        Object inputs = new MixinEntityWrapperFiniteInputs();
        verifyInput(inputs, "cnpcplus$finiteScalar", new Class<?>[]{double.class, CallbackInfo.class},
                new Object[]{0.0, new CallbackInfo("setMotionX", false)},
                new Object[]{Double.NaN, new CallbackInfo("setMotionX", false)});
        verifyInput(new MixinWrapperFinitePosition(), "cnpcplus$finitePosition", new Class<?>[]{double.class, double.class, double.class, CallbackInfo.class},
                new Object[]{1.0, -64.0, 2.0, new CallbackInfo("setPosition", false)},
                new Object[]{1.0, -64.0, Double.POSITIVE_INFINITY, new CallbackInfo("setPosition", false)});
        verifyInput(new MixinWrapperFiniteRotation(), "cnpcplus$finiteRotation", new Class<?>[]{float.class, CallbackInfo.class},
                new Object[]{360.0f, new CallbackInfo("setRotation", false)},
                new Object[]{Float.NaN, new CallbackInfo("setRotation", false)});
        verifyInput(inputs, "cnpcplus$finitePitch", new Class<?>[]{float.class, CallbackInfo.class},
                new Object[]{0.0f, new CallbackInfo("setPitch", false)},
                new Object[]{Float.NaN, new CallbackInfo("setPitch", false)});
        verifyInput(inputs, "cnpcplus$finiteKnockback", new Class<?>[]{int.class, float.class, CallbackInfo.class},
                new Object[]{3, 90.0f, new CallbackInfo("knockback", false)},
                new Object[]{3, Float.NEGATIVE_INFINITY, new CallbackInfo("knockback", false)});
        System.out.println("PASS: invalid script inputs fail before writes; valid values accepted");
    }

    private static void verifyInput(Object instance, String name, Class<?>[] types, Object[] good, Object[] bad) throws Exception {
        Method method = instance.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(instance, good);
        try {
            method.invoke(instance, bad);
            throw new AssertionError(name + " accepted invalid input");
        } catch (InvocationTargetException expected) {
            check(expected.getCause() instanceof CustomNPCsException, "wrong script exception");
            check(expected.getCause().getMessage().contains("requires finite numbers"), "missing diagnostic");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
