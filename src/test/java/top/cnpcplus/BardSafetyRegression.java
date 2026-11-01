package top.cnpcplus;

import java.util.ArrayList;
import java.util.List;
import top.cnpcplus.bard.BardSoundGuard;
import top.cnpcplus.bard.SongListStore;

/** Headless checks for malformed IDs and playable-only weighted selection. */
public final class BardSafetyRegression {
    public static void main(String[] args) {
        resourceIds();
        selection();
        System.out.println("BARD_SAFETY_REGRESSION=PASS");
    }

    private static void resourceIds() {
        check(BardSoundGuard.parseLocation("minecraft:music.creative") != null, "valid sound ID rejected");
        check(BardSoundGuard.parseLocation("Bad Namespace:music") == null, "invalid sound ID accepted");
        check(BardSoundGuard.parseLocation(" minecraft:music.creative") == null, "leading whitespace accepted");
        check(BardSoundGuard.parseLocation("") == null, "empty sound ID accepted");
        System.out.println("PASS: malformed sound IDs are rejected before MusicController");
    }

    private static void selection() {
        List<String[]> songs = new ArrayList<>();
        songs.add(null);
        songs.add(new String[]{"broken:missing", "999"});
        songs.add(new String[]{"test:good_a", "0"});
        songs.add(new String[]{"test:good_b", "4"});
        songs.add(new String[]{"incomplete"});

        String next = SongListStore.pick(songs, "test:good_a", song -> song.startsWith("test:good"));
        check("test:good_b".equals(next), "invalid/current song was not skipped");

        next = SongListStore.pick(songs, "", song -> song.equals("test:good_a"));
        check("test:good_a".equals(next), "single playable song was not retained");

        next = SongListStore.pick(songs, "", song -> false);
        check(next == null, "all-invalid playlist did not become silent");

        next = SongListStore.pick(List.<String[]>of(new String[]{"test:only", "0"}),
                "test:only", song -> true);
        check("test:only".equals(next), "looping single-song playlist did not retain its song");
        System.out.println("PASS: missing songs are skipped while usable songs remain selectable");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
