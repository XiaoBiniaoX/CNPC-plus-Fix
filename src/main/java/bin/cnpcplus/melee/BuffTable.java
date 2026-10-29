package bin.cnpcplus.melee;

import java.util.Map;

/** 一份终版GUI用于近战与远程；get仅用于读取，修改走下列操作。 */
public interface BuffTable {
    Map<String, int[]> get();
    void put(String registryName, int amplifier, int seconds);
    void remove(String registryName);
    void clear();
}
