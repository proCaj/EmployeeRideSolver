package com.vrp.service;

import com.graphhopper.GraphHopper;

import java.io.File;

/**
 * Offline builder for the GraphHopper routing graph cache.
 *
 * <p>{@link GraphHopperService} refuses to build the cache at server startup (a first-time
 * import over a country-sized OSM extract takes minutes and would block boot). Instead it
 * loads a cache that must already exist. This class is that missing build step — the exact
 * command the service logs when the cache is absent:
 *
 * <pre>
 *   mvn exec:java -Dexec.mainClass=com.vrp.service.GraphHopperBuilder
 * </pre>
 *
 * <p>It uses {@link GraphHopperService#configure} so the graph is written with the identical
 * profile/CH configuration the runtime engine loads — no config drift. It fails loudly if the
 * OSM {@code .pbf} is missing, since a silent Haversine fallback would defeat real-routing tests.
 *
 * <p>Arguments (all optional, resolved in this order):
 * <ol>
 *   <li>{@code args[0]} or {@code -Dgraphhopper.osm.file=} or env {@code GRAPHHOPPER_PBF} — OSM file path
 *       (default {@code rheinland-pfalz-latest.osm.pbf})</li>
 *   <li>{@code args[1]} or {@code -Dgraphhopper.cache.dir=} — cache directory
 *       (default {@code ./graphhopper-cache})</li>
 * </ol>
 */
public final class GraphHopperBuilder {

    private GraphHopperBuilder() {
    }

    public static void main(String[] args) {
        String osmFile = arg(args, 0, "graphhopper.osm.file", "GRAPHHOPPER_PBF", "rheinland-pfalz-latest.osm.pbf");
        String cacheDir = arg(args, 1, "graphhopper.cache.dir", null, "./graphhopper-cache");

        build(osmFile, cacheDir);
    }

    /**
     * Builds (or refreshes) the routing cache. Idempotent: if the cache already exists at
     * {@code cacheDir}, GraphHopper's {@code importOrLoad()} loads it instead of re-importing.
     *
     * @throws IllegalStateException if the OSM file does not exist (fail loudly, no fallback)
     */
    public static void build(String osmFile, String cacheDir) {
        File osm = new File(osmFile);
        if (!osm.exists()) {
            throw new IllegalStateException(
                "OSM file not found at '" + osm.getAbsolutePath() + "'. "
                + "Provide the .pbf (e.g. rheinland-pfalz-latest.osm.pbf) via the GRAPHHOPPER_PBF "
                + "environment variable, the graphhopper.osm.file system property, or the first argument. "
                + "Download: https://download.geofabrik.de/europe/germany/rheinland-pfalz-latest.osm.pbf");
        }

        System.out.println("[GraphHopperBuilder] Building routing cache");
        System.out.println("[GraphHopperBuilder]   osm   = " + osm.getAbsolutePath());
        System.out.println("[GraphHopperBuilder]   cache = " + new File(cacheDir).getAbsolutePath());

        long start = System.currentTimeMillis();
        GraphHopper graphHopper = GraphHopperService.configure(new GraphHopper(), osmFile, cacheDir);
        try {
            graphHopper.importOrLoad();
            long secs = (System.currentTimeMillis() - start) / 1000;
            System.out.println("[GraphHopperBuilder] Cache ready in " + secs + "s at " + cacheDir);
        } finally {
            graphHopper.close();
        }
    }

    private static String arg(String[] args, int index, String sysProp, String envVar, String defaultValue) {
        if (args != null && args.length > index && args[index] != null && !args[index].isBlank()) {
            return args[index];
        }
        String fromSys = System.getProperty(sysProp);
        if (fromSys != null && !fromSys.isBlank()) {
            return fromSys;
        }
        if (envVar != null) {
            String fromEnv = System.getenv(envVar);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv;
            }
        }
        return defaultValue;
    }
}
