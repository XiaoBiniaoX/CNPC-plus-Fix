package top.cnpcplus.transport;

public final class TransportLandingRegression {
    public static void main(String[] args) {
        check(TransportLanding.chooseY(82, 64, true, true, -64, 320).orElseThrow() == 82,
                "supported saved position was not preserved");
        check(TransportLanding.chooseY(210, 64, false, true, -64, 320).orElseThrow() == 64,
                "unsupported saved position did not fall back to ground");
        check(TransportLanding.chooseY(210, 64, false, false, -64, 320).isEmpty(),
                "unsafe target and surface were accepted");
        check(TransportLanding.chooseY(400, 64, true, true, -64, 320).orElseThrow() == 64,
                "out-of-height target was not rejected");
        check(TransportLanding.chooseY(-65, -63, true, false, -64, 320).isEmpty(),
                "out-of-height fallback was accepted");
        System.out.println("TRANSPORT_LANDING_REGRESSION=PASS preserve-valid/fallback-ground/reject-unsafe/build-height");
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
