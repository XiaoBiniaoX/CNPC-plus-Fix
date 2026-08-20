package bin.cnpcplus;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Early mixin loader as FML coremod so MixinBooter gathers it from coremodList.
 * Targets classes that are loaded before late-mixin time (vanilla Layer*,
 * NetHandlerPlayClient when other coremods load it early).
 * NO CNPC imports here.
 */
@IFMLLoadingPlugin.Name("CNPCPlusEarly")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public class CnpcPlusEarlyMixinLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        // Vanilla furnace classes are on the classloader before late-mixin time
        // (FurnaceRecipes has a static SMELTING_BASE instance), so the smelting
        // hooks must be applied here or Mixin fails with
        // MixinTargetAlreadyLoadedException during PREPARE.
        return Arrays.asList("mixins.cnpcplus.early.json", "mixins.cnpcplus.early.animation.json", "mixins.cnpcplus.early.bard.json", "mixins.cnpcplus.early.smelting.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
