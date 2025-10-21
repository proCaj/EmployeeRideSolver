package com.vrp.resource;

import com.vrp.domain.VrpSolution;
import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import com.vrp.service.SolverService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/solve")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SolverResource {
    
    @Inject
    SolverService solverService;
    
    @POST
    public Response solve(@QueryParam("weekStart") String weekStartStr) {
        LocalDate weekStart = weekStartStr != null ? 
            LocalDate.parse(weekStartStr) : 
            LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        
        List<Customer> customers = Customer.listAll();
        List<Employee> employees = Employee.listAll();
        List<ShiftDemand> shifts = ShiftDemand.find("active = true").list();
        
        UUID jobId = solverService.solve(customers, employees, shifts, weekStart);
        
        Map<String, String> response = new HashMap<>();
        response.put("jobId", jobId.toString());
        response.put("status", "SOLVING");
        
        return Response.ok(response).build();
    }
    
    @GET
    @Path("/{jobId}/status")
    public Response getStatus(@PathParam("jobId") UUID jobId) {
        String status = solverService.getStatus(jobId);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", status);
        
        return Response.ok(response).build();
    }
    
    @GET
    @Path("/{jobId}/solution")
    public Response getSolution(@PathParam("jobId") UUID jobId) {
        VrpSolution solution = solverService.getSolution(jobId);
        
        if (solution == null) {
            throw new NotFoundException("Solution not found for job " + jobId);
        }
        
        return Response.ok(solution).build();
    }
    
    @DELETE
    @Path("/{jobId}")
    public Response stopSolving(@PathParam("jobId") UUID jobId) {
        solverService.stopSolving(jobId);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "TERMINATED");
        
        return Response.ok(response).build();
    }
}
