package bin.cnpcplus.script;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.*;
import javax.script.ScriptException;

/** Engine-independent error location; indexes are real source lines, not wrapped display rows. */
public final class ScriptDiagnostic {
    private static final Pattern LOCATION = Pattern.compile("(?:<eval>|<Unknown source>|<script>|Script|script|Unknown Source):(\\d+)(?::(\\d+))?|(?:line number|line|行号)\\s*[:=]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    public final int line, column;
    public final String hash, message;

    public ScriptDiagnostic(int line, int column, String hash, String message) {
        this.line = Math.max(0, line); this.column = Math.max(0, column);
        this.hash = hash; this.message = message.length() > 2048 ? message.substring(0, 2048) : message;
    }
    public static ScriptDiagnostic capture(Throwable error, String script) {
        int line = 0, column = 0;
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof ScriptException && ((ScriptException) cause).getLineNumber() > 0) {
                line = ((ScriptException) cause).getLineNumber(); column = ((ScriptException) cause).getColumnNumber(); break;
            }
        }
        StringWriter text = new StringWriter(); error.printStackTrace(new PrintWriter(text));
        if (line == 0) line = lineFromLog(text.toString());
        String message = error.toString();
        return new ScriptDiagnostic(line, column, hash(script), message);
    }
    public static int lineFromLog(String text) {
        Matcher m = LOCATION.matcher(text);
        if (!m.find()) return 0;
        try { return Integer.parseInt(m.group(1) == null ? m.group(3) : m.group(1)); }
        catch (NumberFormatException e) { return 0; }
    }
    public boolean matches(String text) { return hash.equals(hash(text)); }
    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.replace("\r", "").getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format("%02x", b & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static int[] range(String text, int line) {
        if (line < 1) return new int[]{-1, -1};
        int start = 0;
        for (int i = 1; i < line; i++) {
            start = text.indexOf('\n', start);
            if (start < 0) return new int[]{-1, -1};
            start++;
        }
        int end = text.indexOf('\n', start);
        return new int[]{start, end < 0 ? text.length() : end};
    }
}
