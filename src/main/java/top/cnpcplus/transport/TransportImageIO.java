package top.cnpcplus.transport;

import noppes.npcs.CustomNpcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** 服务端传送点图片文件 IO。路径：&lt;levelSave&gt;/cnpcplus_transport/&lt;key&gt;.png */
public final class TransportImageIO {
    private TransportImageIO() {}

    public static File dir() {
        File base = CustomNpcs.getLevelSaveDirectory();
        if (base == null) return null;
        return new File(base, TransportKeys.DIR_NAME);
    }

    public static File fileFor(String key) {
        String safe = TransportKeys.sanitize(key);
        if (safe.isEmpty()) return null;
        File d = dir();
        return d == null ? null : new File(d, safe + ".png");
    }

    public static byte[] read(String key) {
        File f = fileFor(key);
        if (f == null || !f.isFile()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] data = in.readAllBytes();
            if (data.length == 0 || data.length > TransportKeys.MAX_IMAGE_BYTES) return null;
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean write(String key, byte[] data) {
        if (data == null || data.length == 0 || data.length > TransportKeys.MAX_IMAGE_BYTES) return false;
        File f = fileFor(key);
        if (f == null) return false;
        File d = f.getParentFile();
        if (d != null && !d.exists() && !d.mkdirs()) return false;
        try {
            File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data);
                out.getFD().sync();
            }
            if (f.exists() && !f.delete()) {
                tmp.delete();
                return false;
            }
            return tmp.renameTo(f) || f.exists();
        } catch (IOException e) {
            return false;
        }
    }
}
