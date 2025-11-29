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
    @Produces(MediaType.TEXT_HTML)
    public Response assignEmployee(@PathParam("id") Long shiftId, @QueryParam("employeeId") Long employeeId) {
        ShiftDemand shift = ShiftDemand.findById(shiftId);
        Employee employee = Employee.findById(employeeId);
        
        if (shift == null || employee == null) {
            throw new NotFoundException("Shift or Employee not found");
        }
        
        employee.assignToShift(shift);
        return Response.ok(buildEmployeesHtml(shift)).build();
    }
    
    @DELETE
    @Path("/{id}/unassign/{employeeId}")
    @Transactional
    @Produces(MediaType.TEXT_HTML)
    public Response unassignEmployee(@PathParam("id") Long shiftId, @PathParam("employeeId") Long employeeId) {
        ShiftDemand shift = ShiftDemand.findById(shiftId);
        Employee employee = Employee.findById(employeeId);
        
        if (shift == null || employee == null) {
            throw new NotFoundException("Shift or Employee not found");
        }
        
        employee.unassignFromShift(shift);
        return Response.ok(buildEmployeesHtml(shift)).build();
    }
    
    private String buildEmployeesHtml(ShiftDemand shift) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"shift-employees\" id=\"shift-employees-").append(shift.id).append("\">");
        html.append("<div class=\"assigned-employees-list\">");
        
        for (Employee emp : shift.assignedEmployees) {
            String initials = getInitials(emp.name);
            html.append("<div class=\"assigned-employee\" data-employee-id=\"").append(emp.id).append("\">");
            html.append("<div class=\"mini-avatar\">").append(initials).append("</div>");
            html.append("<span class=\"emp-name\">").append(emp.name).append("</span>");
            html.append("<button class=\"remove-btn\" ");
            html.append("hx-delete=\"/api/shifts/").append(shift.id).append("/unassign/").append(emp.id).append("\" ");
            html.append("hx-target=\"#shift-employees-").append(shift.id).append("\" ");
            html.append("hx-swap=\"outerHTML\" title=\"Remove\">");
            html.append("<svg width=\"12\" height=\"12\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M6 18L18 6M6 6l12 12\"/></svg>");
            html.append("</button></div>");
        }
        
        html.append("<button class=\"add-employee-btn\" onclick=\"openAssignModal(").append(shift.id).append(")\">");
        html.append("<svg width=\"12\" height=\"12\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M12 4v16m8-8H4\"/></svg>");
        html.append("Add employee</button>");
        html.append("</div>");
        html.append("<div class=\"employee-count\"><span class=\"count\">").append(shift.assignedEmployees.size());
        html.append("</span> / ").append(shift.requiredEmployees).append(" required</div>");
        html.append("</div>");
        
        return html.toString();
    }
    
    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.split(" ");
        if (parts.length >= 2) {
            return "" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0);
        }
        return "" + name.charAt(0);
    }
}
