package com.vrp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the fail-loud contract of the offline cache builder (decision: no silent Haversine
 * fallback masquerading as "real" routing). Runs without any OSM asset.
 */
class GraphHopperBuilderTest {

    @Test
    void buildFailsLoudlyWhenOsmFileMissing() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> GraphHopperBuilder.build("definitely-not-a-real-file.osm.pbf", "target/gh-cache-should-not-exist"));

        String msg = ex.getMessage();
        assertTrue(msg.contains("OSM file not found"), "message names the missing file: " + msg);
        assertTrue(msg.contains(".pbf") || msg.contains("GRAPHHOPPER_PBF"),
            "message explains how to provide the file: " + msg);
    }
}
