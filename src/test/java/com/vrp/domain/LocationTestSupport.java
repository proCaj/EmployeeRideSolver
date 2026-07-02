package com.vrp.domain;

/**
 * Test-only bridge to {@link Location}'s package-private routing-cache reset.
 * Lives in {@code com.vrp.domain} so it can reach the package-private seam, and
 * exposes it publicly to test classes in other packages.
 */
public final class LocationTestSupport {

    private LocationTestSupport() {
    }

    /** Clears the process-wide routing cache (see {@link Location#clearRoutingCache()}). */
    public static void clearRoutingCache() {
        Location.clearRoutingCache();
    }
}
