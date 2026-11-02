package top.cnpcplus.marks;

import noppes.npcs.controllers.data.MarkData;

/** Entity-owned fallback: no static entity references or cross-NPC backup. */
public interface MarkFallbackAccess {
    MarkData cnpcplus$markFallback();
    void cnpcplus$markFallback(MarkData data);
}
