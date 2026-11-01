package top.cnpcplus.script;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptException;

/** Engine-independent source location using real source lines rather than wrapped display rows. */
public final class ScriptDiagnostic {
    private static final Pattern LOCATION = Pattern.compile(
            "(?:<eval>|<Unknown source>|<script>|Script|script|Unknown Source):(\\d+)(?::(\\d+))?"
                    + "|(?:line number|line|行号)\\s*[:=]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    public final int line;
    public final int column;
    public final String hash;
    public final String message;
    public final String hintKey;
    public final String subject;
    public final int length;

    public ScriptDiagnostic(int line, int column, String hash, String message) {
        this(line, column, hash, message, "", "", 0);
    }

    public ScriptDiagnostic(int line, int column, String hash, String message,
                            String hintKey, String subject, int length) {
        this.line = Math.max(0, line);
        this.column = Math.max(0, column);
        this.hash = hash == null ? "" : hash;
        String safeMessage = message == null ? "" : message;
        this.message = safeMessage.length() > 2048 ? safeMessage.substring(0, 2048) : safeMessage;
        this.hintKey = hintKey;
        this.subject = subject;
        this.length = length;
    }

    public static ScriptDiagnostic capture(Throwable error, String script) {
        int line = 0;
        int column = 0;
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
            if (cause instanceof ScriptException scriptError && scriptError.getLineNumber() > 0) {
                line = scriptError.getLineNumber();
                column = scriptError.getColumnNumber();
                break;
            }
        }
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        if (line == 0) line = lineFromLog(text.toString());
        return new ScriptDiagnostic(line, column, hash(script), error.toString());
    }

    public static int lineFromLog(String text) {
        Matcher matcher = LOCATION.matcher(text);
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1) == null ? matcher.group(3) : matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean matches(String text) {
        return hash.equals(hash(text));
    }

    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((text == null ? "" : text).replace("\r", "").getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte value : digest) out.append(String.format("%02x", value & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
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
