package bin.cnpcplus;

import bin.cnpcplus.config.CnpcPlusConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

@Mod("cnpcplus")
public class CnpcPlus {
    public static final String MODID = "cnpcplus";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CnpcPlus(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, CnpcPlusConfig.SPEC);
        LOGGER.info("CNPCPlus patch loaded - by Bin");
    }
}
