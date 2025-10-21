package com.vrp.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;

@ApplicationScoped
@Startup
public class GraphHopperService {
    
    private static final Logger LOG = Logger.getLogger(GraphHopperService.class);
    
    @ConfigProperty(name = "graphhopper.osm.file")
    String osmFile;
    
    @ConfigProperty(name = "graphhopper.cache.dir")
    String cacheDir;
    
    private GraphHopper graphHopper;
    private boolean initialized = false;
    
    @PostConstruct
    void init() {
        try {
            LOG.info("Initializing GraphHopper routing engine...");
            
            File osmFileObj = new File(osmFile);
            if (!osmFileObj.exists()) {
                LOG.warn("OSM file not found at: " + osmFile);
                LOG.warn("GraphHopper routing will use fallback (Haversine) distances");
                LOG.warn("To enable real routing, download OSM data:");
                LOG.warn("  wget https://download.geofabrik.de/europe/germany/rheinland-pfalz-latest.osm.pbf");
                return;
            }
            
            graphHopper = new GraphHopper();
            graphHopper.setOSMFile(osmFile);
            graphHopper.setGraphHopperLocation(cacheDir);
            graphHopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest"));
            
            LOG.info("Loading OSM data and building routing graph (this may take a few minutes on first run)...");
            graphHopper.importOrLoad();
            
            initialized = true;
            LOG.info("GraphHopper initialization complete - real routing enabled");
        } catch (Exception e) {
            LOG.error("Failed to initialize GraphHopper: " + e.getMessage());
            LOG.warn("Routing will use fallback (Haversine) distances");
        }
    }
    
    public GraphHopper getGraphHopper() {
        if (!initialized) {
            throw new IllegalStateException("GraphHopper is not initialized - OSM file not found or initialization failed");
        }
        return graphHopper;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    @PreDestroy
    void cleanup() {
        if (graphHopper != null) {
            LOG.info("Closing GraphHopper...");
            graphHopper.close();
        }
    }
}
