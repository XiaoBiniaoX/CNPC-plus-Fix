package top.cnpcplus.quest;

import java.util.ArrayDeque;
import net.minecraft.world.entity.LivingEntity;

/** Synchronous per-call context for CNPC's existing doQuest loop, including recursive area sharing. */
public final class QuestAreaKillContext {
    private static final ThreadLocal<ArrayDeque<LivingEntity>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private QuestAreaKillContext() {}
    public static void push(LivingEntity entity) { STACK.get().push(entity); }
    public static void pop() { ArrayDeque<LivingEntity> stack = STACK.get(); if (!stack.isEmpty()) stack.pop(); if (stack.isEmpty()) STACK.remove(); }
    public static LivingEntity current() { ArrayDeque<LivingEntity> stack = STACK.get(); return stack.peek(); }
}
