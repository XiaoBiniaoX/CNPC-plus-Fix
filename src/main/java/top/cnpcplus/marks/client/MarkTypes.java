package top.cnpcplus.marks.client;

import net.minecraft.resources.ResourceLocation;

public final class MarkTypes {
    public static final String[] NAMES = {"gui.none", "mark.question", "mark.exclamation", "mark.pointer",
            "mark.skull", "mark.cross", "mark.star", "cnpcplus.mark.heart", "cnpcplus.mark.shield",
            "cnpcplus.mark.diamond", "cnpcplus.mark.check", "cnpcplus.mark.flag", "cnpcplus.mark.sword",
            "cnpcplus.mark.coin", "cnpcplus.mark.crown", "cnpcplus.mark.lightning"};
    private static final String[] FILES = {"heart", "shield", "diamond", "check", "flag", "sword", "coin", "crown", "lightning"};
    private static final ResourceLocation[] EXTRA = java.util.Arrays.stream(FILES)
            .map(name -> new ResourceLocation("cnpcplus", "textures/marks/" + name + ".png")).toArray(ResourceLocation[]::new);
    private MarkTypes() {}
    public static ResourceLocation texture(int type, ResourceLocation nativeTexture) {
        return type >= 7 && type - 7 < EXTRA.length ? EXTRA[type - 7] : nativeTexture;
    }
}
