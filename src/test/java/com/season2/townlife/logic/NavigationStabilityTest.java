package com.season2.townlife.logic;

/** Dependency-free arrival, retry and trapdoor safeguards. Run with Java 17 -ea. */
public final class NavigationStabilityTest {
    public static void main(String[] args) {
        assert ArrivalStability.isAtWorkSquare(true, 0.8D, 0.0D);
        assert ArrivalStability.isAtWorkSquare(false, 0.36D, 0.1D) : "near centre counts as arrived";
        assert !ArrivalStability.isAtWorkSquare(false, 2.25D, 0.0D) : "not one block away";
        assert !ArrivalStability.isAtWorkSquare(false, 0.2D, 1.1D) : "not through another floor";
        assert !ArrivalStability.shouldReturn(2.0D, 0.0D, 2) : "no jitter-induced instant return";
        assert ArrivalStability.shouldReturn(2.0D, 0.0D, 3) : "sustained displacement returns";
        assert !ArrivalStability.shouldReturn(0.5D, 0.0D, 20) : "ignore harmless nudges";
        assert PathRetryPolicy.shouldRequest(true, 0, 60, false, 0);
        assert !PathRetryPolicy.shouldRequest(false, 20, 60, true, 4) : "do not flood goals";
        assert !PathRetryPolicy.shouldRequest(false, 61, 60, false, 1) : "leave active path alone";
        assert PathRetryPolicy.shouldRequest(false, 61, 60, true, 0);
        assert TrapdoorPolicy.mayOpen(true, true, false, true, true);
        assert !TrapdoorPolicy.mayOpen(true, false, false, true, true) : "never open over a drop";
        assert !TrapdoorPolicy.mayOpen(true, true, true, true, true) : "do not fight redstone";
        assert !TrapdoorPolicy.mayOpen(false, true, false, true, true) : "only traveling residents";
        assert !TrapdoorPolicy.mayOpen(true, true, false, false, true) : "never force chunk loads";
        assert !TrapdoorPolicy.mayOpen(true, true, false, true, false) : "do not open arbitrary trapdoors";
        System.out.println("NavigationStabilityTest PASS");
    }
}
