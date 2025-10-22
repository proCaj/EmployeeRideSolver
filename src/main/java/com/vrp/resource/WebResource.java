package com.vrp.resource;

import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/")
public class WebResource {

    @Inject
    Template dashboard;
    
    @Inject
    Template employees;
    
    @Inject
    @io.quarkus.qute.Location("employee-form.html")
    Template employeeForm;
    
    @Inject
    Template optimize;
    
    @Inject
    Template routes;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        long employeeCount = Employee.count();
        long customerCount = Customer.count();
        long shiftCount = ShiftDemand.count();
        
        return dashboard
            .data("title", "Dashboard")
            .data("employeeCount", employeeCount)
            .data("customerCount", customerCount)
            .data("shiftCount", shiftCount);
    }
    
    @GET
    @Path("/employees")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listEmployees() {
        List<Employee> employeeList = Employee.listAll();
        return employees
            .data("title", "Employees")
            .data("employees", employeeList);
    }
    
    @GET
    @Path("/employees/new")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance newEmployee() {
        Employee employee = new Employee();
        employee.active = true;
        return employeeForm
            .data("title", "New Employee")
            .data("employee", employee)
            .data("isEdit", false);
    }
    
    @GET
    @Path("/employees/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance editEmployee(@PathParam("id") Long id) {
        Employee employee = Employee.findById(id);
        return employeeForm
            .data("title", "Edit Employee")
            .data("employee", employee)
            .data("isEdit", true);
    }
    
    @GET
    @Path("/optimize")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance optimizePage() {
        return optimize.data("title", "Route Optimization");
    }
    
    @GET
    @Path("/routes")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance routesPage() {
        return routes
            .data("title", "Routes")
            .data("routes", List.of());
    }
    
    @GET
    @Path("/customers")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance customersPage() {
        return dashboard
            .data("title", "Customers - Coming Soon")
            .data("employeeCount", Employee.count())
            .data("customerCount", Customer.count())
            .data("shiftCount", ShiftDemand.count());
    }
    
    @GET
    @Path("/shifts")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance shiftsPage() {
        return dashboard
            .data("title", "Shifts - Coming Soon")
            .data("employeeCount", Employee.count())
            .data("customerCount", Customer.count())
            .data("shiftCount", ShiftDemand.count());
    }
}
