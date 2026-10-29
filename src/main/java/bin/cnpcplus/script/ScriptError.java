package bin.cnpcplus.script;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptException;
import net.minecraft.nbt.CompoundTag;

/** 只作编辑器诊断，不影响脚本执行。行号对应原生getFullCode的主脚本前缀。 */
public record ScriptError(int line, String sourceHash, String message) {
    public static final String KEY = "CNPCPlusScriptError";
    private static final Pattern LOCATION = Pattern.compile(
            "(?:<eval>|<script>|<stdin>):(\\d+)(?::\\d+)?|at line number\\s+(\\d+)");

    public static ScriptError from(String source, Throwable error) {
        if (source == null || error == null) return null;
        Throwable cause = error;
        for (int i = 0; cause != null && i < 16; i++, cause = cause.getCause()) {
            if (cause instanceof ScriptException scriptError && scriptError.getLineNumber() > 0) {
                String message = scriptError.toString();
                return new ScriptError(scriptError.getLineNumber(), hash(source),
                        message.substring(0, Math.min(512, message.length())));
            }
        }
        java.io.StringWriter output = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(output));
        return parse(source, output.toString());
    }

    public static ScriptError parse(String source, String output) {
        if (source == null || output == null) return null;
        Matcher matcher = LOCATION.matcher(output);
        if (!matcher.find()) return null; // 无可靠行号时不猜位置。
        try {
            int line = Integer.parseInt(matcher.group(1) == null ? matcher.group(2) : matcher.group(1));
            if (line < 1) return null;
            String message = output.lines().filter(s -> !s.isBlank()).findFirst().orElse("");
            return new ScriptError(line, hash(source), message.substring(0, Math.min(512, message.length())));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public boolean matches(String source) { return sourceHash.equals(hash(source)); }

    public int[] range(String source) {
        if (!matches(source)) return null;
        int start = 0;
        for (int i = 1; i < line; i++) {
            int newline = source.indexOf('\n', start);
            if (newline < 0) return null; // 外部附加脚本报错，不标到主脚本上。
            start = newline + 1;
        }
        int end = source.indexOf('\n', start);
        return new int[]{start, end < 0 ? source.length() : end};
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Line", line);
        tag.putString("SourceHash", sourceHash);
        tag.putString("Message", message);
        return tag;
    }

    public static ScriptError load(CompoundTag tag) {
        int line = tag.getInt("Line");
        String hash = tag.getString("SourceHash");
        String message = tag.getString("Message");
        if (line < 1 || line > 1000000 || !hash.matches("[0-9a-f]{64}") || message.length() > 512) return null;
        return new ScriptError(line, hash, message);
    }

    private static String hash(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
