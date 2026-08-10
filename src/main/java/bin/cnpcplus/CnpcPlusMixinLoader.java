package bin.cnpcplus;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * Late mixin loader for MixinBooter.
 * Keep this class free of any CNPC imports so it can be discovered early safely.
 */
public class CnpcPlusMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.cnpcplus.json");
    }
}
