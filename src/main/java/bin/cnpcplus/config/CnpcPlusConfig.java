package bin.cnpcplus.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class CnpcPlusConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> DIALOG_TEXT_COLOR = BUILDER
            .comment(
                "对话文本基础十六进制颜色 (RGB)",
                "例: E0E0E0 / #FFFFFF / FFAA00",
                "若对话内使用了&颜色码，则优先使用&颜色"
            )
            .define("dialogTextColor", "E0E0E0");

    public static final ModConfigSpec.ConfigValue<String> DIALOG_TEXT_FORMAT = BUILDER
            .comment(
                "对话文本默认&格式前缀 (可叠加样式)",
                "颜色: &0-&9 &a-&f | 样式: &l粗体 &o斜体 &n下划线 &m删除线 &k乱码 &r重置",
                "例: &l &o &6&l  空=不附加格式"
            )
            .define("dialogTextFormat", "");

    public static final ModConfigSpec.ConfigValue<String> DIALOG_OPTION_COLOR = BUILDER
            .comment(
                "对话选项基础十六进制颜色 (RGB)",
                "例: E0E0E0 / #FFFFFF / 55FF55",
                "若选项内使用了&颜色码，则优先使用&颜色"
            )
            .define("dialogOptionColor", "E0E0E0");

    public static final ModConfigSpec.ConfigValue<String> DIALOG_OPTION_FORMAT = BUILDER
            .comment(
                "对话选项默认&格式前缀 (可叠加样式)",
                "颜色: &0-&9 &a-&f | 样式: &l粗体 &o斜体 &n下划线 &m删除线 &k乱码 &r重置",
                "例: &7 &b&o  空=不附加格式"
            )
            .define("dialogOptionFormat", "");

    public static final ModConfigSpec SPEC = BUILDER.build();
}
