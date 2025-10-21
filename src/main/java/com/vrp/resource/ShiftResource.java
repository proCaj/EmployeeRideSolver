package com.vrp.resource;

import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/shifts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShiftResource {
    
    @GET
    @Path("/{id}")
    public ShiftDemand get(@PathParam("id") Long id) {
        ShiftDemand shift = ShiftDemand.findById(id);
        if (shift == null) {
            throw new NotFoundException("Shift not found");
        }
        return shift;
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    public ShiftDemand update(@PathParam("id") Long id, ShiftDemand updatedShift) {
        ShiftDemand shift = ShiftDemand.findById(id);
        if (shift == null) {
            throw new NotFoundException("Shift not found");
        }
        shift.shiftType = updatedShift.shiftType;
        shift.dayOfWeek = updatedShift.dayOfWeek;
        shift.startTime = updatedShift.startTime;
        shift.endTime = updatedShift.endTime;
        shift.requiredEmployees = updatedShift.requiredEmployees;
        shift.earlyArrivalBufferMinMinutes = updatedShift.earlyArrivalBufferMinMinutes;
        shift.earlyArrivalBufferMaxMinutes = updatedShift.earlyArrivalBufferMaxMinutes;
        shift.requiresReturnTrip = updatedShift.requiresReturnTrip;
        shift.active = updatedShift.active;
        return shift;
    }
    
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        ShiftDemand shift = ShiftDemand.findById(id);
        if (shift == null) {
            throw new NotFoundException("Shift not found");
        }
        shift.delete();
        return Response.noContent().build();
    }
    
    @PATCH
    @Path("/{id}/toggle")
    @Transactional
    public ShiftDemand toggleActive(@PathParam("id") Long id) {
        ShiftDemand shift = ShiftDemand.findById(id);
        if (shift == null) {
            throw new NotFoundException("Shift not found");
        }
        shift.active = !shift.active;
        return shift;
    }
    
    @POST
    @Path("/{id}/assign-employee")
    @Transactional
    public Response assignEmployee(@PathParam("id") Long shiftId, @QueryParam("employeeId") Long employeeId) {
        ShiftDemand shift = ShiftDemand.findById(shiftId);
        Employee employee = Employee.findById(employeeId);
        
        if (shift == null || employee == null) {
            throw new NotFoundException("Shift or Employee not found");
        }
        
        employee.assignToShift(shift);
        return Response.ok().build();
    }
    
    @DELETE
    @Path("/{id}/unassign/{employeeId}")
    @Transactional
    public Response unassignEmployee(@PathParam("id") Long shiftId, @PathParam("employeeId") Long employeeId) {
        ShiftDemand shift = ShiftDemand.findById(shiftId);
        Employee employee = Employee.findById(employeeId);
        
        if (shift == null || employee == null) {
            throw new NotFoundException("Shift or Employee not found");
        }
        
        employee.unassignFromShift(shift);
        return Response.ok().build();
    }
}
