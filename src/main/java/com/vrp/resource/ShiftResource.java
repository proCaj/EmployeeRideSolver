package com.vrp.resource;

import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Path("/api/shifts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ShiftResource {
    
    @GET
    public List<ShiftDemand> listAll() {
        return ShiftDemand.listAll();
    }
    
    @GET
    @Path("/{id}")
    public ShiftDemand get(@PathParam("id") Long id) {
        ShiftDemand shift = ShiftDemand.findById(id);
        if (shift == null) {
            throw new NotFoundException("Shift not found");
        }
        return shift;
    }
    
    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createFromForm(
            @FormParam("customerId") Long customerId,
            @FormParam("dayOfWeek") String dayOfWeek,
            @FormParam("shiftType") String shiftType,
            @FormParam("startTime") String startTime,
            @FormParam("endTime") String endTime,
            @FormParam("requiredEmployees") Integer requiredEmployees,
            @FormParam("earlyArrivalBufferMin") Integer earlyArrivalBufferMin,
            @FormParam("earlyArrivalBufferMax") Integer earlyArrivalBufferMax,
            @FormParam("requiresReturnTrip") Boolean requiresReturnTrip,
            @FormParam("active") Boolean active) {
        
        Customer customer = Customer.findById(customerId);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        
        ShiftDemand shift = new ShiftDemand();
        shift.customer = customer;
        shift.dayOfWeek = DayOfWeek.valueOf(dayOfWeek);
        shift.shiftType = shiftType;
        shift.startTime = LocalTime.parse(startTime);
        shift.endTime = LocalTime.parse(endTime);
        shift.requiredEmployees = requiredEmployees;
        shift.earlyArrivalBufferMinMinutes = (long) (earlyArrivalBufferMin != null ? earlyArrivalBufferMin : 15);
        shift.earlyArrivalBufferMaxMinutes = (long) (earlyArrivalBufferMax != null ? earlyArrivalBufferMax : 30);
        shift.requiresReturnTrip = requiresReturnTrip != null && requiresReturnTrip;
        shift.active = active != null && active;
        
        shift.persist();
        return Response.status(Response.Status.CREATED).entity(shift).build();
    }
    
    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(ShiftDemand shift) {
        if (shift.customer != null && shift.customer.id != null) {
            Customer customer = Customer.findById(shift.customer.id);
            if (customer == null) {
                throw new NotFoundException("Customer not found");
            }
            shift.customer = customer;
        }
        shift.persist();
        return Response.status(Response.Status.CREATED).entity(shift).build();
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public ShiftDemand updateFromForm(
            @PathParam("id") Long id,
            @FormParam("customerId") Long customerId,
            @FormParam("dayOfWeek") String dayOfWeek,
            @FormParam("shiftType") String shiftType,
            @FormParam("startTime") String startTime,
            @FormParam("endTime") String endTime,
            @FormParam("requiredEmployees") Integer requiredEmployees,
            @FormParam("earlyArrivalBufferMin") Integer earlyArrivalBufferMin,
            @FormParam("earlyArrivalBufferMax") Integer earlyArrivalBufferMax,
            @FormParam("requiresReturnTrip") Boolean requiresReturnTrip,
            @FormParam("active") Boolean active) {
        
        ShiftDemand shift = ShiftDemand.findById(id);
        if (shift == null) {
            throw new NotFoundException("Shift not found");
        }
        
        Customer customer = Customer.findById(customerId);
        if (customer == null) {
            throw new NotFoundException("Customer not found");
        }
        
        shift.customer = customer;
        shift.dayOfWeek = DayOfWeek.valueOf(dayOfWeek);
        shift.shiftType = shiftType;
        shift.startTime = LocalTime.parse(startTime);
        shift.endTime = LocalTime.parse(endTime);
        shift.requiredEmployees = requiredEmployees;
        shift.earlyArrivalBufferMinMinutes = (long) (earlyArrivalBufferMin != null ? earlyArrivalBufferMin : 15);
        shift.earlyArrivalBufferMaxMinutes = (long) (earlyArrivalBufferMax != null ? earlyArrivalBufferMax : 30);
        shift.requiresReturnTrip = requiresReturnTrip != null && requiresReturnTrip;
        shift.active = active != null && active;
        
        return shift;
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public ShiftDemand update(@PathParam("id") Long id, ShiftDemand updatedShift) {
        ShiftDemand shift = ShiftDemand.findById(id);
        if (shift == null) {
            throw new NotFoundException("Shift not found");
        }
        
        if (updatedShift.customer != null && updatedShift.customer.id != null) {
            Customer customer = Customer.findById(updatedShift.customer.id);
            if (customer != null) {
                shift.customer = customer;
            }
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
