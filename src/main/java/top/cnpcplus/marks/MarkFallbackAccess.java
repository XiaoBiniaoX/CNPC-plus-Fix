package top.cnpcplus.marks;

import noppes.npcs.controllers.data.MarkData;

/** Entity-owned data cannot collide across client/server IDs, previews or dimension changes. */
public interface MarkFallbackAccess {
    MarkData cnpcplus$markFallback();
    void cnpcplus$markFallback(MarkData data);
}
