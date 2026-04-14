package com.vrp.resource;

import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.VrpSolution;
import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import com.vrp.service.SolverService;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Path("/api/solver")
public class SolverResource {
    
    @Inject
    SolverService solverService;
    
    @Inject
    Template solverStatus;
    
    @Inject
    Template solverResults;
    
    @POST
    @Path("/solve")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance solve(
            @FormParam("date") String dateStr,
            @FormParam("maxRuntime") Integer maxRuntime,
            @FormParam("minimizeDistance") String minimizeDistance,
            @FormParam("maximizeConsecutive") String maximizeConsecutive) {
        
        LocalDate targetDate = dateStr != null && !dateStr.isEmpty() ? 
            LocalDate.parse(dateStr) : LocalDate.now();
        
        LocalDate weekStart = targetDate.with(java.time.DayOfWeek.MONDAY);
        
        List<Customer> customers = Customer.listAll();
        List<Employee> employees = Employee.listAll();
        List<ShiftDemand> shifts = ShiftDemand.find("active = true").list();
        
        int runtime = maxRuntime != null ? maxRuntime : 30;
        
        UUID jobId = solverService.solve(customers, employees, shifts, weekStart, runtime);
        
        Map<String, Object> data = new HashMap<>();
        data.put("status", "Solving");
        data.put("statusClass", "solving");
        data.put("jobId", jobId.toString());
        data.put("score", "-");
        data.put("timeElapsed", "0s");
        data.put("iterations", "-");
        
        return solverStatus.data(data);
    }
    
    @GET
    @Path("/status")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getStatus() {
        UUID currentJobId = solverService.getCurrentJobId();
        
        Map<String, Object> data = new HashMap<>();
        
        if (currentJobId == null) {
            data.put("status", "Idle");
            data.put("statusClass", "idle");
            data.put("jobId", null);
            data.put("score", "-");
            data.put("timeElapsed", "-");
            data.put("iterations", "-");
            return solverStatus.data(data);
        }
        
        String status = solverService.getStatus(currentJobId);
        VrpSolution solution = solverService.getBestSolution(currentJobId);
        long startTime = solverService.getStartTime(currentJobId);
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        
        String scoreStr = "-";
        String statusClass = "idle";
        String displayStatus = status;
        
        if (solution != null && solution.getScore() != null) {
            scoreStr = solution.getScore().toString();
        }
        
        switch (status) {
            case "SOLVING_ACTIVE":
                statusClass = "solving";
                displayStatus = "Solving";
                break;
            case "NOT_SOLVING":
                statusClass = "completed";
                displayStatus = "Completed";
                break;
            case "SOLVING_SCHEDULED":
                statusClass = "solving";
                displayStatus = "Starting";
                break;
            default:
                statusClass = "idle";
        }
        
        data.put("status", displayStatus);
        data.put("statusClass", statusClass);
        data.put("jobId", currentJobId.toString());
        data.put("score", scoreStr);
        data.put("timeElapsed", formatDuration(elapsed));
        data.put("iterations", "-");
        
        return solverStatus.data(data);
    }
    
    @POST
    @Path("/stop")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance stopSolving() {
        UUID currentJobId = solverService.getCurrentJobId();
        
        if (currentJobId != null) {
            solverService.stopSolving(currentJobId);
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("status", "Stopped");
        data.put("statusClass", "stopped");
        data.put("jobId", null);
        data.put("score", "-");
        data.put("timeElapsed", "-");
        data.put("iterations", "-");
        
        return solverStatus.data(data);
    }
    
    @GET
    @Path("/debug")
    @Produces(MediaType.APPLICATION_JSON)
    public Response debugInfo() {
        Map<String, Object> data = new HashMap<>();
        
        UUID currentJobId = solverService.getCurrentJobId();
        data.put("currentJobId", currentJobId != null ? currentJobId.toString() : null);
        
        long customerCount = Customer.count();
        long employeeCount = Employee.count();
        long shiftCount = ShiftDemand.count();
        long activeShiftCount = ShiftDemand.find("active = true").count();
        
        data.put("customerCount", customerCount);
        data.put("employeeCount", employeeCount);
        data.put("shiftCount", shiftCount);
        data.put("activeShiftCount", activeShiftCount);
        
        List<ShiftDemand> activeShifts = ShiftDemand.find("active = true").list();
        List<Map<String, Object>> shiftInfo = new ArrayList<>();
        for (ShiftDemand s : activeShifts) {
            Map<String, Object> info = new HashMap<>();
            info.put("id", s.id);
            info.put("customer", s.customer != null ? s.customer.name : "NULL");
            info.put("dayOfWeek", s.dayOfWeek != null ? s.dayOfWeek.toString() : "NULL");
            info.put("startTime", s.startTime != null ? s.startTime.toString() : "NULL");
            info.put("endTime", s.endTime != null ? s.endTime.toString() : "NULL");
            info.put("assignedCount", s.assignedEmployees != null ? s.assignedEmployees.size() : -1);
            info.put("active", s.active);
            info.put("requiresReturnTrip", s.requiresReturnTrip);
            shiftInfo.add(info);
        }
        data.put("shifts", shiftInfo);
        
        if (currentJobId != null) {
            VrpSolution bestSolution = solverService.getBestSolution(currentJobId);
            if (bestSolution != null) {
                data.put("bestSolutionScore", bestSolution.getScore() != null ? bestSolution.getScore().toString() : "NULL");
                data.put("bestSolutionEventCount", bestSolution.getEvents() != null ? bestSolution.getEvents().size() : "NULL");
                data.put("bestSolutionDriverCount", bestSolution.getDrivers() != null ? bestSolution.getDrivers().size() : "NULL");
                
                if (bestSolution.getEvents() != null) {
                    List<Map<String, Object>> eventDetails = new ArrayList<>();
                    for (Event e : bestSolution.getEvents()) {
                        Map<String, Object> ed = new HashMap<>();
                        ed.put("id", e.getId());
                        ed.put("pickup", e.isPickup());
                        ed.put("driver", e.getDriver() != null ? e.getDriver().getId() : "NULL");
                        ed.put("arrivalTime", e.getArrivalTime() != null ? e.getArrivalTime().toString() : "NULL");
                        ed.put("departureTime", e.getDepartureTime() != null ? e.getDepartureTime().toString() : "NULL");
                        ed.put("shiftDate", e.getShiftDate() != null ? e.getShiftDate().toString() : "NULL");
                        ed.put("passengers", e.getPassengerCount());
                        ed.put("delta", e.getPassengerDelta());
                        ed.put("peak", e.getPeakPassengerCount());
                        ed.put("cumPassengers", e.getCumulativePassengerCount());
                        ed.put("duration", e.getDuration() != null ? e.getDuration().toString() : "NULL");
                        ed.put("minStartTime", e.getMinStartTime() != null ? e.getMinStartTime().toString() : "NULL");
                        ed.put("maxEndTime", e.getMaxEndTime() != null ? e.getMaxEndTime().toString() : "NULL");
                        eventDetails.add(ed);
                    }
                    data.put("events", eventDetails);
                }
            } else {
                data.put("bestSolutionScore", "NO_SOLUTION");
            }
            
            String status = solverService.getStatus(currentJobId);
            data.put("solverStatus", status);
        }
        
        return Response.ok(data).build();
    }

    @GET
    @Path("/results")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getResults() {
        UUID currentJobId = solverService.getCurrentJobId();
        
        Map<String, Object> data = new HashMap<>();
        
        if (currentJobId == null) {
            data.put("hasResults", false);
            return solverResults.data(data);
        }
        
        VrpSolution solution = solverService.getBestSolution(currentJobId);
        
        if (solution == null || solution.getDrivers() == null || solution.getDrivers().isEmpty()) {
            data.put("hasResults", false);
            return solverResults.data(data);
        }
        
        Map<Driver, List<Event>> eventsByDriver = new HashMap<>();
        for (Event event : solution.getEvents()) {
            if (event.getDriver() != null) {
                eventsByDriver.computeIfAbsent(event.getDriver(), k -> new ArrayList<>()).add(event);
            }
        }
        
        List<Map<String, Object>> driverRoutes = new ArrayList<>();
        int totalStops = 0;
        long totalDistance = 0;
        
        for (Driver driver : solution.getDrivers()) {
            List<Event> events = eventsByDriver.get(driver);
            if (events == null || events.isEmpty()) continue;
            
            long driverDistance = 0;
            for (Event event : events) {
                driverDistance += event.getDistance();
            }
            
            Map<String, Object> route = new HashMap<>();
            String driverName = driver.getEmployee() != null ? driver.getEmployee().name : driver.getId();
            route.put("driverName", driverName);
            route.put("stopCount", events.size());
            route.put("distance", String.format("%.1f km", driverDistance / 1000.0));
            route.put("events", events);
            
            driverRoutes.add(route);
            totalStops += events.size();
            totalDistance += driverDistance;
        }
        
        data.put("hasResults", !driverRoutes.isEmpty());
        data.put("driverRoutes", driverRoutes);
        data.put("totalStops", totalStops);
        data.put("totalDistance", String.format("%.1f km", totalDistance / 1000.0));
        data.put("score", solution.getScore() != null ? solution.getScore().toString() : "-");
        
        return solverResults.data(data);
    }
    
    @GET
    @Path("/{jobId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStatusJson(@PathParam("jobId") UUID jobId) {
        String status = solverService.getStatus(jobId);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", status);
        
        return Response.ok(response).build();
    }
    
    @GET
    @Path("/{jobId}/solution")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSolution(@PathParam("jobId") UUID jobId) {
        VrpSolution solution = solverService.getSolution(jobId);
        
        if (solution == null) {
            throw new NotFoundException("Solution not found for job " + jobId);
        }
        
        return Response.ok(solution).build();
    }
    
    @DELETE
    @Path("/{jobId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stopSolvingById(@PathParam("jobId") UUID jobId) {
        solverService.stopSolving(jobId);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "TERMINATED");
        
        return Response.ok(response).build();
    }
    
    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }
}
