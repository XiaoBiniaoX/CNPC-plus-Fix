package top.cnpcplus.clone.client;

/** Implemented by the three native clone-selection screens, not a global screen registry. */
public interface ClonePreviewHost {
    ClonePreview cnpcplus$clonePreview();

    default void cnpcplus$receivePreview(int tab, String name, long request, byte[] bytes) {
        ClonePreview preview = cnpcplus$clonePreview();
        if (preview != null) preview.receive(tab, name, request, bytes);
    }
}
