package bin.cnpcplus.config;

import bin.cnpcplus.CnpcPlus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CnpcPlusGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        Configuration cfg = CnpcPlusConfig.getConfig();
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        if (cfg != null) {
            for (String catName : cfg.getCategoryNames()) {
                ConfigCategory cat = cfg.getCategory(catName);
                if (cat.isEmpty()) continue;
                elements.add(new ConfigElement(cat));
            }
        }
        return new CnpcPlusGuiConfig(parentScreen, elements);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    private static class CnpcPlusGuiConfig extends GuiConfig {
        CnpcPlusGuiConfig(GuiScreen parent, List<IConfigElement> elements) {
            super(parent, elements, CnpcPlus.MODID, false, false, CnpcPlus.NAME);
        }

        @Override
        public void onGuiClosed() {
            super.onGuiClosed();
            CnpcPlusConfig.save();
        }
    }
}
