package top.cnpcplus.bard;

import noppes.npcs.roles.JobBard;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

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
        List<String[]> songs = SONGS.get(job);
        if (songs == null || songs.isEmpty()) {
            return current.isEmpty() ? null : current;
        }
        int total = 0;
        for (String[] e : songs) {
            if (e[0].equals(current)) continue;
            total += parseWeight(e[1]) + 1;
        }
        if (total == 0) return songs.get(0)[0];
        int r = new Random().nextInt(total);
        for (String[] e : songs) {
            if (e[0].equals(current)) continue;
            r -= parseWeight(e[1]) + 1;
            if (r < 0) return e[0];
        }
        return songs.get(0)[0];
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
