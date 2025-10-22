package com.vrp.service;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
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
    
    private final ConcurrentMap<UUID, VrpSolution> solutionMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, SolverJob<VrpSolution, UUID>> jobMap = new ConcurrentHashMap<>();
    
    public UUID solve(List<Customer> customers, List<Employee> employees, 
                      List<ShiftDemand> shiftDemands, LocalDate weekStart) {
        UUID problemId = UUID.randomUUID();
        
        LOG.info("Starting solve job " + problemId + " for week " + weekStart);
        
        List<Employee> driverEmployees = employees.stream()
            .filter(e -> e.employeeType == EmployeeType.DRIVER && e.active)
            .collect(Collectors.toList());
        
        List<Employee> siteEmployees = employees.stream()
            .filter(e -> e.employeeType == EmployeeType.SITE_EMPLOYEE && e.active)
            .collect(Collectors.toList());
        
        LOG.info("Found " + driverEmployees.size() + " active drivers and " + siteEmployees.size() + " active site employees");
        
        List<Event> events = eventGenerationService.generateEventsForWeek(shiftDemands, weekStart);
        
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
            weekStart
        );
        
        SolverJob<VrpSolution, UUID> solverJob = solverManager.solve(problemId, problem);
        jobMap.put(problemId, solverJob);
        
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
    }
    
    private List<Driver> createDriversFromEmployees(List<Employee> driverEmployees) {
        List<Driver> drivers = new ArrayList<>();
        for (Employee employee : driverEmployees) {
            Driver driver = new Driver("DRIVER-" + employee.id, Location.HUB, employee);
            driver.setMaxCapacity(6);
            drivers.add(driver);
            LOG.info("Created driver from employee: " + employee.name + " (ID: " + employee.id + ")");
        }
        LOG.info("Created " + drivers.size() + " driver(s) for optimization");
        return drivers;
    }
}
