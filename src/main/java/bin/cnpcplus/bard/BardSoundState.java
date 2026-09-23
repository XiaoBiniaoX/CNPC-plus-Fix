package bin.cnpcplus.bard;

import net.minecraft.client.audio.ISound;

/** Shared sound identity without an early dependency on CustomNPCs classes. */
public final class BardSoundState {
    public static ISound playing;

    private BardSoundState() {
    }
}
