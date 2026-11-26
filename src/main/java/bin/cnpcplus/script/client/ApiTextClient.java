package bin.cnpcplus.script.client;

import net.minecraft.network.chat.Component;
import top.cnpcplus.api.script.ApiText;

/** Only the displaying client resolves translations. Literal strings never enter the translation lookup. */
public final class ApiTextClient {
    private ApiTextClient() {}
    public static String resolve(ApiText text) {
        return text.translatable() ? Component.translatable(text.value(), text.arguments().toArray()).getString() : text.value();
    }
}
