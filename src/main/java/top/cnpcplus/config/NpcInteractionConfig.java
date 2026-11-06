package top.cnpcplus.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config: cnpcplus-interaction.toml. */
public final class NpcInteractionConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ALLOW_INVISIBLE_NPC_INTERACTION;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ALLOW_INVISIBLE_NPC_INTERACTION = builder
                .comment("允许玩家与可见性为关的 NPC 交互，默认关闭；不改变渲染、阵营、攻击状态或脚本取消规则。",
                        "Allow player interaction with NPCs whose Visibility is Off. Default: false. Other interaction rules remain unchanged.")
                .translation("cnpcplus.config.interaction.allowInvisibleNpc")
                .define("allow_invisible_npc_interaction", false);
        SPEC = builder.build();
    }

    private NpcInteractionConfig() {}
}
