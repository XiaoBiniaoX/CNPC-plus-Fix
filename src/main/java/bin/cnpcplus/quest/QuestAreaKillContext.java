package bin.cnpcplus.quest;

import java.util.ArrayDeque;
import net.minecraft.entity.EntityLivingBase;

public final class QuestAreaKillContext {
    private static final ThreadLocal<ArrayDeque<EntityLivingBase>> STACK = new ThreadLocal<ArrayDeque<EntityLivingBase>>() {
        @Override protected ArrayDeque<EntityLivingBase> initialValue() { return new ArrayDeque<EntityLivingBase>(); }
    };
    private QuestAreaKillContext() {}
    public static void push(EntityLivingBase entity) { STACK.get().push(entity); }
    public static void pop() {
        ArrayDeque<EntityLivingBase> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }
    public static EntityLivingBase current() { return STACK.get().peek(); }
}
