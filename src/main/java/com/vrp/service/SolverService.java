package com.vrp.service;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.domain.VrpSolution;
import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.EmployeeType;
import com.vrp.entity.ShiftDemand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import com.graphhopper.GraphHopper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class SolverService {
    
    private static final Logger LOG = Logger.getLogger(SolverService.class);
    
    @Inject
    SolverManager<VrpSolution, UUID> solverManager;
    
    @Inject
    EventGenerationService eventGenerationService;
    
    @Inject
    GraphHopperService graphHopperService;
    
    private final ConcurrentMap<UUID, VrpSolution> solutionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, VrpSolution> bestSolutionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SolverJob<VrpSolution, UUID>> jobMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> startTimeMap = new ConcurrentHashMap<>();
    private volatile UUID currentJobId = null;
    
    public UUID solve(List<Customer> customers, List<Employee> employees, 
                      List<ShiftDemand> shiftDemands, LocalDate weekStart) {
        return solve(customers, employees, shiftDemands, weekStart, 30);
    }
    
    public UUID solve(List<Customer> customers, List<Employee> employees, 
                      List<ShiftDemand> shiftDemands, LocalDate weekStart, int maxRuntimeSeconds) {
        
        if (currentJobId != null) {
            stopSolving(currentJobId);
        }
        
        UUID problemId = UUID.randomUUID();
        currentJobId = problemId;
        startTimeMap.put(problemId, System.currentTimeMillis());
        
        LOG.info("Starting solve job " + problemId + " for week " + weekStart + " with max runtime " + maxRuntimeSeconds + "s");
        
        List<Employee> driverEmployees = employees.stream()
            .filter(e -> e.employeeType == EmployeeType.DRIVER && e.active)
            .collect(Collectors.toList());
        
        List<Employee> siteEmployees = employees.stream()
            .filter(e -> e.employeeType == EmployeeType.SITE_EMPLOYEE && e.active)
            .collect(Collectors.toList());
        
        LOG.info("Found " + driverEmployees.size() + " active drivers and " + siteEmployees.size() + " active site employees");
        LOG.info("Found " + shiftDemands.size() + " active shift demands");
        LOG.info("Found " + customers.size() + " customers");
        
        if (driverEmployees.isEmpty()) {
            LOG.warn("No active drivers found - optimization may not produce useful results");
        }
        
        if (shiftDemands.isEmpty()) {
            LOG.warn("No active shift demands found - nothing to optimize");
        }

        List<Event> events = eventGenerationService.generateEventsForWeek(shiftDemands, weekStart, Location.HUB);
        LOG.info("Generated " + events.size() + " events for optimization");

        List<Driver> drivers = createDriversFromEmployees(driverEmployees);
        
        List<Location> locations = new ArrayList<>();
        locations.add(Location.HUB);
        customers.forEach(c -> locations.add(new Location(c.name, c.latitude, c.longitude)));
        
        VrpSolution problem = new VrpSolution(
            locations,
            customers,
            employees,
            drivers,
            events,
            weekStart,
            getGraphHopperInstance()
        );
        
        try {
            SolverJob<VrpSolution, UUID> solverJob = solverManager.solveBuilder()
                .withProblemId(problemId)
                .withProblem(problem)
                .withBestSolutionConsumer(solution -> {
                    bestSolutionMap.put(problemId, solution);
                    LOG.info("New best solution for job " + problemId + " with score: " + solution.getScore());
                })
                .withFinalBestSolutionConsumer(solution -> {
                    solutionMap.put(problemId, solution);
                    bestSolutionMap.put(problemId, solution);
                    LOG.info("Final solution for job " + problemId + " with score: " + solution.getScore());
                })
                .run();
            
            jobMap.put(problemId, solverJob);
            LOG.info("Solver job " + problemId + " started successfully");
            
        } catch (Exception e) {
            LOG.error("Failed to start solver job: " + e.getMessage(), e);
            currentJobId = null;
            throw new RuntimeException("Failed to start optimization: " + e.getMessage(), e);
        }
        
        return problemId;
    }
    
    public VrpSolution getSolution(UUID problemId) {
        SolverJob<VrpSolution, UUID> solverJob = jobMap.get(problemId);
        if (solverJob != null) {
            try {
                VrpSolution solution = solverJob.getFinalBestSolution();
                if (solution != null) {
                    solutionMap.put(problemId, solution);
                    LOG.info("Retrieved solution for job " + problemId + " with score: " + solution.getScore());
                }
                return solution;
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                LOG.warn("Could not get solution for job " + problemId + ": " + e.getMessage());
            }
        }
        return solutionMap.get(problemId);
    }
    
    public VrpSolution getBestSolution(UUID problemId) {
        return bestSolutionMap.get(problemId);
    }
    
    public String getStatus(UUID problemId) {
        SolverJob<VrpSolution, UUID> solverJob = jobMap.get(problemId);
        if (solverJob == null) {
            return solutionMap.containsKey(problemId) ? "COMPLETED" : "NOT_FOUND";
        }
        return solverJob.getSolverStatus().name();
    }
    
    public void stopSolving(UUID problemId) {
        SolverJob<VrpSolution, UUID> solverJob = jobMap.get(problemId);
        if (solverJob != null) {
            solverJob.terminateEarly();
            LOG.info("Terminated solve job " + problemId);
        }
        if (problemId.equals(currentJobId)) {
            currentJobId = null;
        }
    }
    
    public UUID getCurrentJobId() {
        return currentJobId;
    }
    
    public long getStartTime(UUID problemId) {
        return startTimeMap.getOrDefault(problemId, System.currentTimeMillis());
    }
    
    private List<Driver> createDriversFromEmployees(List<Employee> driverEmployees) {
        List<Driver> drivers = new ArrayList<>();
        for (Employee employee : driverEmployees) {
            Location homeLocation = employee.getHomeLocation(Location.HUB);
            Driver driver = new Driver("DRIVER-" + employee.id, homeLocation, employee);
            driver.setMaxCapacity(6);
            drivers.add(driver);
            LOG.info("Created driver from employee: " + employee.name + " (ID: " + employee.id + ") with home location: " + homeLocation.name());
        }
        LOG.info("Created " + drivers.size() + " driver(s) for optimization");
        return drivers;
    }
    
    private GraphHopper getGraphHopperInstance() {
        try {
            if (graphHopperService.isInitialized()) {
                return graphHopperService.getGraphHopper();
            }
        } catch (Exception e) {
            LOG.warn("GraphHopper not available for solver: " + e.getMessage());
        }
        return null;
    }
}
