package top.cnpcplus.bard;

import noppes.npcs.roles.JobBard;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public class SongListStore {
    private static final Map<JobBard, List<String[]>> SONGS = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    public static List<String[]> get(JobBard job) {
        return SONGS.get(job);
    }

    public static void set(JobBard job, List<String[]> songs) {
        synchronized (LOCK) {
            if (songs == null || songs.isEmpty()) {
                SONGS.remove(job);
            } else {
                SONGS.put(job, songs);
            }
        }
    }

    public static String pick(JobBard job, String current) {
        return pick(SONGS.get(job), current, song -> true);
    }

    public static String pick(JobBard job, String current, Predicate<String> playable) {
        return pick(SONGS.get(job), current, playable);
    }

    /** Pure weighted selector used by both normal playback and the loop keeper. */
    public static String pick(List<String[]> songs, String current, Predicate<String> playable) {
        if (songs == null || songs.isEmpty()) {
            return current == null || current.isEmpty() || !allowed(playable, current) ? null : current;
        }
        String first = null;
        int total = 0;
        for (String[] e : songs) {
            if (!valid(e) || !allowed(playable, e[0])) continue;
            if (first == null) first = e[0];
            if (e[0].equals(current)) continue;
            total += parseWeight(e[1]) + 1;
        }
        if (total == 0) return first;
        int r = new Random().nextInt(total);
        for (String[] e : songs) {
            if (!valid(e) || e[0].equals(current) || !allowed(playable, e[0])) continue;
            r -= parseWeight(e[1]) + 1;
            if (r < 0) return e[0];
        }
        return first;
    }

    private static boolean valid(String[] entry) {
        return entry != null && entry.length >= 2 && entry[0] != null && !entry[0].isEmpty();
    }

    private static boolean allowed(Predicate<String> playable, String song) {
        try {
            return playable == null || playable.test(song);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static int parseWeight(String w) {
        try {
            return Math.max(0, Integer.parseInt(w.trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    public static String desc(List<String[]> songs) {
        StringBuilder sb = new StringBuilder();
        for (String[] e : songs) {
            sb.append(e[0]).append('*').append(parseWeight(e[1]) + 1).append(' ');
        }
        return sb.toString().trim();
    }
}
