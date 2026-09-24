package com.season2.townlife.logic;

/** Separate arrival and departure thresholds stop one-block path oscillation. */
public final class ArrivalStability {
    private ArrivalStability() {}

    public static boolean isAtWorkSquare(boolean sameFeetBlock, double horizontalDistanceSqr,
                                         double verticalDistance) {
        return sameFeetBlock || (horizontalDistanceSqr <= 0.64D && verticalDistance <= 0.55D);
    }

    public static boolean shouldReturn(double horizontalDistanceSqr, double verticalDistance,
                                       int consecutiveSecondsOutside) {
        return consecutiveSecondsOutside >= 3
                && (horizontalDistanceSqr > 1.21D || verticalDistance > 1.05D);
    }
}
