package bin.cnpcplus.accessor;

import noppes.npcs.ModelPartConfig;

public interface EquipmentModelDataAccessor {
    ModelPartConfig getMainhand();
    ModelPartConfig getOffhand();
    ModelPartConfig getHelmet();
    ModelPartConfig getChestplate();
    ModelPartConfig getLeggings();
    ModelPartConfig getBoots();
}
