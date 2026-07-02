package com.vrp.e2e;

import com.vrp.domain.LocationTestSupport;
import com.vrp.service.GraphHopperService;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base for tests that require the REAL GraphHopper routing engine (no Haversine fallback).
 *
 * <p>GraphHopper loads asynchronously at startup, so a solve fired before the graph is ready would
 * silently fall back to Haversine and quietly invalidate the whole "real routing" premise. Every
 * subclass therefore waits, before each test, until the engine reports initialized — failing loudly
 * (timeout) if it never does. It also clears the process-wide routing cache so values computed by
 * earlier Haversine-based tests in the same JVM cannot leak in.
 *
 * <p>Subclasses must carry {@code @EnabledIfEnvironmentVariable(named = "GRAPHHOPPER_PBF", ...)} so
 * they only run when the operator has provided the OSM extract (see the {@code real-routing} Maven
 * profile that builds the cache from it before the test JVM starts).
 */
public abstract class RealRoutingTestBase {

    @Inject
    protected GraphHopperService graphHopperService;

    @BeforeEach
    void awaitRealRoutingReady() {
        LocationTestSupport.clearRoutingCache();

        await("GraphHopper real routing engine to finish loading")
            .atMost(Duration.ofMinutes(3))
            .pollInterval(Duration.ofSeconds(2))
            .until(graphHopperService::isInitialized);

        assertTrue(graphHopperService.isInitialized(),
            "GraphHopper must be initialized for real-routing tests. If this fails, the OSM cache "
            + "was not built — set GRAPHHOPPER_PBF and run under the 'real-routing' Maven profile.");
    }
}
