package top.cnpcplus.shader.client;

public final class ShaderLoadFailureCacheRegression {
    public static void main(String[] args) {
        ShaderLoadFailureCache cache = new ShaderLoadFailureCache();
        String broken = "test:broken";
        String other = "test:other";

        check(!cache.contains(broken), "new shader was already cached as failed");
        cache.add(broken);
        cache.add(other);
        check(cache.contains(broken) && cache.contains(other), "failed IDs were not cached");
        cache.remove(broken); // Explicit single-shader reload.
        check(!cache.contains(broken) && cache.contains(other), "single reload cleared the wrong failures");
        cache.clear(); // Resource reload.
        check(!cache.contains(other), "resource reload did not clear failure cache");
        System.out.println("SHADER_LOAD_FAILURE_CACHE_REGRESSION=PASS");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
