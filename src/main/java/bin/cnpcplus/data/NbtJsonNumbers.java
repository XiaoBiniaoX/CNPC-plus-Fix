package bin.cnpcplus.data;

/** CNPC lowercases numeric tokens, while the JDK requires Infinity/NaN case. */
public final class NbtJsonNumbers {
    private NbtJsonNumbers() { }
    private static String canonical(String token) {
        return token.replace("infinity", "Infinity").replace("nan", "NaN");
    }
    public static double parseDouble(String token) { return Double.parseDouble(canonical(token)); }
    public static float parseFloat(String token) { return Float.parseFloat(canonical(token)); }
}
