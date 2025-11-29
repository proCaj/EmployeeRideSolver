package com.vrp.service;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.CustomModel;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
@Startup
public class GraphHopperService {
    
    private static final Logger LOG = Logger.getLogger(GraphHopperService.class);
    
    @ConfigProperty(name = "graphhopper.osm.file")
    String osmFile;
    
    @ConfigProperty(name = "graphhopper.cache.dir")
    String cacheDir;
    
    private GraphHopper graphHopper;
    private volatile boolean initialized = false;
    private volatile boolean initializing = false;
    
    @PostConstruct
    void init() {
        File osmFileObj = new File(osmFile);
        if (!osmFileObj.exists()) {
            LOG.warn("OSM file not found at: " + osmFile);
            LOG.warn("GraphHopper routing will use fallback (Haversine) distances");
            LOG.warn("To enable real routing, download OSM data:");
            LOG.warn("  wget https://download.geofabrik.de/europe/germany/rheinland-pfalz-latest.osm.pbf");
            return;
        }
        
        File cacheFile = new File(cacheDir + "/edges");
        if (cacheFile.exists()) {
            loadGraphHopperAsync();
        } else {
            LOG.warn("GraphHopper cache not found - first-time initialization would block server startup");
            LOG.warn("To build the cache, run: mvn exec:java -Dexec.mainClass=com.vrp.service.GraphHopperBuilder");
            LOG.warn("Using fallback (Haversine) distances instead");
        }
    }
    
    private void loadGraphHopperAsync() {
        initializing = true;
        CompletableFuture.runAsync(() -> {
            try {
                LOG.info("Loading GraphHopper routing engine from cache...");
                
                graphHopper = new GraphHopper();
                graphHopper.setOSMFile(osmFile);
                graphHopper.setGraphHopperLocation(cacheDir);
                
                Profile carProfile = new Profile("car")
                    .setVehicle("car")
                    .setWeighting("custom")
                    .setCustomModel(new CustomModel());
                graphHopper.setProfiles(carProfile);
                graphHopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
                
                graphHopper.importOrLoad();
                
                initialized = true;
                initializing = false;
                LOG.info("GraphHopper initialization complete - real routing enabled");
            } catch (Exception e) {
                initializing = false;
                LOG.error("Failed to initialize GraphHopper: " + e.getMessage());
                LOG.warn("Routing will use fallback (Haversine) distances");
            }
        });
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
    
    public boolean isInitializing() {
        return initializing;
    }
    
    @PreDestroy
    void cleanup() {
        if (graphHopper != null) {
            LOG.info("Closing GraphHopper...");
            graphHopper.close();
        }
    }
}
