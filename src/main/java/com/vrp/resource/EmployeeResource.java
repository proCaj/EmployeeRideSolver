package com.vrp.resource;

import com.vrp.entity.Employee;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/employees")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmployeeResource {
    
    @GET
    public List<Employee> listAll() {
        return Employee.listAll();
    }
    
    @GET
    @Path("/{id}")
    public Employee get(@PathParam("id") Long id) {
        Employee employee = Employee.findById(id);
        if (employee == null) {
            throw new NotFoundException("Employee not found");
        }
        return employee;
    }
    
    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response createFromForm(
            @FormParam("name") String name,
            @FormParam("email") String email,
            @FormParam("phoneNumber") String phoneNumber) {
        Employee employee = new Employee();
        employee.name = name;
        employee.email = (email != null && !email.trim().isEmpty()) ? email : null;
        employee.phoneNumber = (phoneNumber != null && !phoneNumber.trim().isEmpty()) ? phoneNumber : null;
        employee.persist();
        return Response.status(Response.Status.CREATED).entity(employee).build();
    }
    
    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(Employee employee) {
        employee.persist();
        return Response.status(Response.Status.CREATED).entity(employee).build();
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Employee updateFromForm(
            @PathParam("id") Long id,
            @FormParam("name") String name,
            @FormParam("email") String email,
            @FormParam("phoneNumber") String phoneNumber) {
        Employee employee = Employee.findById(id);
        if (employee == null) {
            throw new NotFoundException("Employee not found");
        }
        employee.name = name;
        employee.email = (email != null && !email.trim().isEmpty()) ? email : null;
        employee.phoneNumber = (phoneNumber != null && !phoneNumber.trim().isEmpty()) ? phoneNumber : null;
        return employee;
    }
    
    @PUT
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.APPLICATION_JSON)
    public Employee update(@PathParam("id") Long id, Employee updatedEmployee) {
        Employee employee = Employee.findById(id);
        if (employee == null) {
            throw new NotFoundException("Employee not found");
        }
        employee.name = updatedEmployee.name;
        employee.email = updatedEmployee.email;
        employee.phoneNumber = updatedEmployee.phoneNumber;
        employee.skills = updatedEmployee.skills;
        employee.active = updatedEmployee.active;
        return employee;
    }
    
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") Long id) {
        Employee employee = Employee.findById(id);
        if (employee == null) {
            throw new NotFoundException("Employee not found");
        }
        employee.delete();
        return Response.noContent().build();
    }
    
    @PATCH
    @Path("/{id}/toggle")
    @Transactional
    public Employee toggleActive(@PathParam("id") Long id) {
        Employee employee = Employee.findById(id);
        if (employee == null) {
            throw new NotFoundException("Employee not found");
        }
        employee.active = !employee.active;
        return employee;
    }
}
