package bin.cnpcplus.bard;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.SoundCategory;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

/**
 * Dynamically extends the SoundCategory enum with a "bard" entry so the
 * vanilla sound options screen (GuiScreenOptionsSounds) renders a native
 * slider for it and GameSettings stores its volume under its own key,
 * fully independent of MUSIC/RECORDS.
 *
 * Must run before any sound-options GUI is opened (FMLClientSetupEvent is
 * early enough). Enum members cannot be added at the language level, so we
 * do it with Unsafe + reflection:
 * 1. build an instance without running the constructor
 * 2. set name + ordinal (the slider's button id IS category.ordinal())
 * 3. append it to $VALUES (values() returns $VALUES.clone())
 * 4. register it in SOUND_CATEGORIES so getByName()/getSoundCategoryNames()
 *    and options.txt round-trip work
 * 5. migrate the old config BardVolume in as the initial value iff the
 *    vanilla settings map has no bard entry yet
 */
public final class BardSoundCategory {

    public static final String NAME = "bard";

    public static SoundCategory BARD;

    private BardSoundCategory() {
    }

    public static void init() {
        if (BARD != null) return;
        try {
            SoundCategory master = SoundCategory.MASTER;

            Unsafe unsafe = getUnsafe();
            if (unsafe == null) return;
            SoundCategory bard = (SoundCategory) unsafe.allocateInstance(SoundCategory.class);
            setField(SoundCategory.class.getDeclaredField("name"), bard, NAME);
            setField(Enum.class.getDeclaredField("ordinal"), bard, master.ordinal() + 1);

            Field valuesField = SoundCategory.class.getDeclaredField("$VALUES");
            valuesField.setAccessible(true);
            SoundCategory[] oldValues = (SoundCategory[]) valuesField.get(null);
            SoundCategory[] nextValues = Arrays.copyOf(oldValues, oldValues.length + 1);
            nextValues[oldValues.length] = bard;
            valuesField.set(null, nextValues);

            Field mapField = SoundCategory.class.getDeclaredField("SOUND_CATEGORIES");
            mapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, SoundCategory> categories = (Map<String, SoundCategory>) mapField.get(null);
            categories.put(NAME, bard);

            BARD = bard;
            migrateLegacyVolume();
        } catch (Throwable ignored) {
        }
    }

    private static void migrateLegacyVolume() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return;
            GameSettings settings = mc.gameSettings;
            if (settings == null) return;
            Field levelsField = GameSettings.class.getDeclaredField("soundLevels");
            levelsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<SoundCategory, Float> levels = (Map<SoundCategory, Float>) levelsField.get(settings);
            if (!levels.containsKey(BARD)) {
                settings.setSoundLevel(BARD, (float) CnpcPlusConfig.getBardVolume());
            }
        } catch (Throwable ignored) {
        }
    }

    private static Unsafe getUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void setField(Field field, Object target, Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (Throwable ignored) {
        }
    }
}